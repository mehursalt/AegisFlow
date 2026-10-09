package blossom.project.core;

import blossom.project.common.config.Rule;
import blossom.project.core.filter.Filter;
import blossom.project.core.filter.FilterAspect;
import com.alibaba.fastjson.JSON;
import java.util.*;

/** Structural checks before a complete rule set can replace the live snapshot. */
final class RuleValidator {
    private static final Set<String> FILTER_IDS = new HashSet<>();
    static {
        ServiceLoader.load(Filter.class).stream().forEach(provider -> {
            FilterAspect aspect=provider.type().getAnnotation(FilterAspect.class);
            if(aspect!=null) FILTER_IDS.add(aspect.id());
        });
    }
    static void validate(List<Rule> rules) {
        if(rules==null) throw new IllegalArgumentException("rules must be an array");
        Set<String> ids=new HashSet<>(), paths=new HashSet<>();
        for(Rule rule:rules) {
            if(rule==null) throw new IllegalArgumentException("rule cannot be null");
            text(rule.getId(),"id"); text(rule.getServiceId(),"serviceId");
            if(!ids.add(rule.getId())) throw new IllegalArgumentException("duplicate rule id");
            if(!Set.of("http","dubbo").contains(rule.getProtocol()==null?"":rule.getProtocol())) throw new IllegalArgumentException("unsupported protocol");
            path(rule.getPrefix());
            if(rule.getPaths()==null || rule.getPaths().isEmpty()) throw new IllegalArgumentException("paths cannot be empty");
            for(String path:rule.getPaths()) {
                path(path);
                if(!paths.add(rule.getServiceId()+"."+path)) throw new IllegalArgumentException("duplicate service/path");
            }
            if(rule.getMaxConcurrentRequests()<0) throw new IllegalArgumentException("negative concurrency limit");
            Rule.RetryConfig retry=rule.getRetryConfig();
            if(retry==null || retry.getTimes()<0 || retry.getTimes()>3 || retry.getTotalTimeoutMs()<0 || retry.getAttemptTimeoutMs()<0 || retry.getBackoffMs()<0) throw new IllegalArgumentException("invalid retry settings");
            if(rule.getFilterConfigs()==null) throw new IllegalArgumentException("filterConfigs cannot be null");
            for(Rule.FilterConfig filter:rule.getFilterConfigs()) {
                if(filter==null || !FILTER_IDS.contains(filter.getId())) throw new IllegalArgumentException("unknown filter id");
                if(filter.getConfig()!=null && !filter.getConfig().isBlank()) {
                    var config=JSON.parseObject(filter.getConfig());
                    if(config==null) throw new IllegalArgumentException("filter config must be object");
                    if("load_balance_filter".equals(filter.getId())) {
                        String strategy=config.getString("load_balance");
                        if(strategy!=null && !Set.of("Random","RoundRobin","Weight","ConsistentHash").contains(strategy)) throw new IllegalArgumentException("unknown load balance strategy");
                    }
                }
            }
        }
    }
    private static void text(String value,String field) {
        if(value==null || value.isBlank() || value.length()>128) throw new IllegalArgumentException("invalid "+field);
    }
    private static void path(String value) {
        if(value==null || !value.startsWith("/") || value.length()>2048) throw new IllegalArgumentException("invalid path/prefix");
    }
}
