package blossom.project.config.center.nacos.impl;


import blossom.project.common.config.Rule;
import blossom.project.config.center.api.ConfigCenter;
import blossom.project.config.center.api.RulesChangeListener;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executor;


/**
 * 这个类就用来实现我们配置中心的具体方法
 */

@Slf4j
public class NacosConfigCenter implements ConfigCenter {

    /**
     * 需要拉取的服务配置的DATA_ID 要求自定义
     */
    private static final String DATA_ID = "api-gateway";


    /**
     * 服务端地址
     */
    private String serverAddr;

    /**
     * 环境
     */
    private String env;

    /**
     * Nacos提供的与配置中心进行交互的接口
     */
    private ConfigService configService;

    @Override
    public void init(String serverAddr, String env) {
        this.serverAddr = serverAddr;
        this.env = env;

        try {
            this.configService = NacosFactory.createConfigService(serverAddr);
        } catch (NacosException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void subscribeRulesChange(RulesChangeListener listener) {
        try {
            //初始化通知 DATA_ID是自己定义的 返回值就是一个json
            String configJson = configService.getConfig(DATA_ID, env, 5000);
            //configJson : {"rules":[{}, {}]}
            List<Rule> rules = parseRules(configJson);
            //调用我们的监听器 参数就是我们拿到的rules
            listener.onRulesChange(rules);

            //监听变化
            configService.addListener(DATA_ID, env, new Listener() {
                //是否使用额外线程执行
                @Override
                public Executor getExecutor() {
                    return null;
                }
                //这里的用法我在那片线程池动态调参的时候写到过,有兴趣可以查看博客
                @Override
                public void receiveConfigInfo(String configInfo) {
                    try {
                        listener.onRulesChange(parseRules(configInfo));
                        log.info("gateway_rules_applied");
                    } catch(RuntimeException invalid) {
                        // Keep the last valid live snapshot. Do not log arbitrary configuration content.
                        log.warn("gateway_rules_rejected type={}",invalid.getClass().getSimpleName());
                    }
                }
            });

        } catch (NacosException e) {
            throw new RuntimeException(e);
        }
    }
    private static List<Rule> parseRules(String json) {
        if(json==null || json.isBlank()) throw new IllegalArgumentException("missing rules configuration");
        var object=JSON.parseObject(json);
        if(object==null || !(object.get("rules") instanceof com.alibaba.fastjson.JSONArray)) throw new IllegalArgumentException("rules must be an array");
        return object.getJSONArray("rules").toJavaList(Rule.class);
    }
}
