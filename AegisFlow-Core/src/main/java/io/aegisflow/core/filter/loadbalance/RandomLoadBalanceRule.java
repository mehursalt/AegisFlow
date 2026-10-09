package io.aegisflow.core.filter.loadbalance;

import io.aegisflow.core.DynamicConfigManager;
import io.aegisflow.common.config.ServiceInstance;
import io.aegisflow.common.exception.NotFoundException;
import io.aegisflow.core.context.GatewayContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static io.aegisflow.common.enums.ResponseCode.SERVICE_INSTANCE_NOT_FOUND;

/**
 * 当前类用于提供随机抽取后端服务的负载均衡实现
 */
@Slf4j
public class RandomLoadBalanceRule implements LoadBalanceGatewayRule {


    private final String serviceId;

    /**
     * 服务列表
     */
    private Set<ServiceInstance> serviceInstanceSet;

    public RandomLoadBalanceRule(String serviceId) {
        this.serviceId = serviceId;
    }

    private static ConcurrentHashMap<String, RandomLoadBalanceRule> serviceMap = new ConcurrentHashMap<>();

    public static RandomLoadBalanceRule getInstance(String serviceId) {
        RandomLoadBalanceRule loadBalanceRule = serviceMap.get(serviceId);
        if (loadBalanceRule == null) {
            loadBalanceRule = new RandomLoadBalanceRule(serviceId);
            serviceMap.put(serviceId, loadBalanceRule);
        }
        return loadBalanceRule;
    }


    @Override
    public ServiceInstance choose(GatewayContext ctx,boolean gray) {
        String serviceId = ctx.getUniqueId();
        return choose(serviceId,gray);
    }

    @Override
    public ServiceInstance choose(String serviceId,boolean gray) {
        Set<ServiceInstance> serviceInstanceSet =
                DynamicConfigManager.getInstance().getServiceInstanceByUniqueId(serviceId,gray);
        if (serviceInstanceSet.isEmpty()) {
            log.warn("No instance available for:{}", serviceId);
            throw new NotFoundException(SERVICE_INSTANCE_NOT_FOUND);
        }
        List<ServiceInstance> instances = new ArrayList<ServiceInstance>(serviceInstanceSet);
        int index = ThreadLocalRandom.current().nextInt(instances.size());
        ServiceInstance instance = (ServiceInstance) instances.get(index);
        return instance;
    }
}
