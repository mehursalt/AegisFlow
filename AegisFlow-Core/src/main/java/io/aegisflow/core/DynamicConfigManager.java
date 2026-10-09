package io.aegisflow.core;

import io.aegisflow.common.config.Rule;
import io.aegisflow.common.config.ServiceDefinition;
import io.aegisflow.common.config.ServiceInstance;
import io.aegisflow.core.filter.loadbalance.ConsistentHashLoadBalanceRule;
import org.apache.commons.collections.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 动态服务缓存配置管理类  用于缓存从配置中心拉取下来的配置
 * 这个类可能不太好理解 如果有了解Nacos源码的可能会好理解一点
 * 推荐在理解这个类的作用之前先查看我的博客
 */
public class DynamicConfigManager {

	//	服务的定义集合：uniqueId代表服务的唯一标识
	private ConcurrentHashMap<String /* uniqueId */ , ServiceDefinition>  serviceDefinitionMap = new ConcurrentHashMap<>();

	//	服务的实例集合：uniqueId与一对服务实例对应
	private ConcurrentHashMap<String /* uniqueId */ , Set<ServiceInstance>>  serviceInstanceMap = new ConcurrentHashMap<>();

	//	规则集合
	private record RuleSnapshot(Map<String,Rule> byId, Map<String,Rule> byPath, Map<String,List<Rule>> byService) {}
	private volatile RuleSnapshot ruleSnapshot = new RuleSnapshot(Map.of(),Map.of(),Map.of());

	//路径以及规则集合
		private DynamicConfigManager() {
	}

	private static class SingletonHolder {
		private static final DynamicConfigManager INSTANCE = new DynamicConfigManager();
	}



	/***************** 	对服务定义缓存进行操作的系列方法 	***************/

	public static DynamicConfigManager getInstance() {
		return SingletonHolder.INSTANCE;
	}

	public void putServiceDefinition(String uniqueId,
									 ServiceDefinition serviceDefinition) {
		serviceDefinitionMap.put(uniqueId, serviceDefinition);;
	}

	public ServiceDefinition getServiceDefinition(String uniqueId) {
		return serviceDefinitionMap.get(uniqueId);
	}

	public void removeServiceDefinition(String uniqueId) {
		serviceDefinitionMap.remove(uniqueId);
	}

	public ConcurrentHashMap<String, ServiceDefinition> getServiceDefinitionMap() {
		return serviceDefinitionMap;
	}

	/***************** 	对服务实例缓存进行操作的系列方法 	***************/

	public Set<ServiceInstance> getServiceInstanceByUniqueId(String uniqueId, boolean gray){
		Set<ServiceInstance> serviceInstances = serviceInstanceMap.get(uniqueId);
		if (CollectionUtils.isEmpty(serviceInstances)) {
			return Collections.emptySet();
		}
		//不为空且为灰度流量
		if (gray) {
			return  serviceInstances.stream()
					.filter(ServiceInstance::isGray)
					.collect(Collectors.toSet());
		}

		return serviceInstances;
	}

	public void addServiceInstance(String uniqueId, ServiceInstance serviceInstance) {
		Set<ServiceInstance> set = serviceInstanceMap.get(uniqueId);
		set.add(serviceInstance);
	}

	public void addServiceInstance(String uniqueId, Set<ServiceInstance> serviceInstanceSet) {
		serviceInstanceMap.put(uniqueId, serviceInstanceSet);
	}

	public void updateServiceInstance(String uniqueId, ServiceInstance serviceInstance) {
		Set<ServiceInstance> set = serviceInstanceMap.get(uniqueId);
		Iterator<ServiceInstance> it = set.iterator();
		while(it.hasNext()) {
			ServiceInstance is = it.next();
			if(is.getServiceInstanceId().equals(serviceInstance.getServiceInstanceId())) {
				it.remove();
				break;
			}
		}
		set.add(serviceInstance);
	}

	public void removeServiceInstance(String uniqueId, String serviceInstanceId) {
		Set<ServiceInstance> set = serviceInstanceMap.get(uniqueId);
		Iterator<ServiceInstance> it = set.iterator();
		while(it.hasNext()) {
			ServiceInstance is = it.next();
			if(is.getServiceInstanceId().equals(serviceInstanceId)) {
				it.remove();
				break;
			}
		}
	}

	public void removeServiceInstancesByUniqueId(String uniqueId) {
		serviceInstanceMap.remove(uniqueId);
	}


	/***************** 	对规则缓存进行操作的系列方法 	***************/

	public synchronized void putRule(String ruleId, Rule rule) {
		if(rule==null || !Objects.equals(ruleId,rule.getId())) throw new IllegalArgumentException("rule id mismatch");
		Map<String,Rule> replacement=new HashMap<>(ruleSnapshot.byId()); replacement.put(ruleId,rule);
		putAllRule(new ArrayList<>(replacement.values()));
	}

	public synchronized void putAllRule(List<Rule> ruleList) {
		RuleValidator.validate(ruleList);
		// Detach nested collections from the caller before publishing.
		ruleList=com.alibaba.fastjson.JSON.parseArray(com.alibaba.fastjson.JSON.toJSONString(ruleList),Rule.class);
		ConcurrentHashMap<String,Rule> newRuleMap = new ConcurrentHashMap<>();
		ConcurrentHashMap<String,Rule> newPathMap = new ConcurrentHashMap<>();
		ConcurrentHashMap<String,List<Rule>> newServiceMap = new ConcurrentHashMap<>();
		for(Rule rule : ruleList){
			newRuleMap.put(rule.getId(),rule);
			List<Rule> rules = newServiceMap.get(rule.getServiceId());
			if(rules == null){
				rules = new ArrayList<>();
			}
			rules.add(rule);
			newServiceMap.put(rule.getServiceId(),rules);

			List<String> paths = rule.getPaths();
			for(String path :paths){
				String key = rule.getServiceId()+"."+path;
				newPathMap.put(key,rule);
			}
		}
		newServiceMap.replaceAll((key,value)->List.copyOf(value));
		ruleSnapshot = new RuleSnapshot(Map.copyOf(newRuleMap),Map.copyOf(newPathMap),Map.copyOf(newServiceMap));
	}

	public Rule getRule(String ruleId) {
		return ruleSnapshot.byId().get(ruleId);
	}

	public synchronized void removeRule(String ruleId) {
		Map<String,Rule> replacement=new HashMap<>(ruleSnapshot.byId()); replacement.remove(ruleId);
		putAllRule(new ArrayList<>(replacement.values()));
	}

	public ConcurrentHashMap<String, Rule> getRuleMap() {
		return new ConcurrentHashMap<>(ruleSnapshot.byId());
	}

	public Rule  getRuleByPath(String path){
		return ruleSnapshot.byPath().get(path);
	}

	public List<Rule>  getRuleByServiceId(String serviceId){
		return ruleSnapshot.byService().getOrDefault(serviceId,List.of());
	}
	public Rule resolveRule(String serviceId,String path) {
		RuleSnapshot snapshot=ruleSnapshot;
		Rule exact=snapshot.byPath().get(serviceId+"."+path);
		if(exact!=null) return exact;
		return snapshot.byService().getOrDefault(serviceId,List.of()).stream()
			.filter(rule->path.startsWith(rule.getPrefix())).findFirst().orElse(null);
	}
}
