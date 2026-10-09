package io.aegisflow.core.filter.loadbalance;

import io.aegisflow.common.config.ServiceInstance;
import io.aegisflow.core.context.GatewayContext;

/**
 * LoadBalanceGatewayRule类
 * 负载均衡顶级接口
 */
public interface LoadBalanceGatewayRule {

    /**
     * 通过上下文参数获取服务实例
     * @param ctx
     * @param gray
     * @return
     */
    @Deprecated
    ServiceInstance choose(GatewayContext ctx,boolean gray);

    /**
     * 通过服务ID拿到对应的服务实例
     * @param serviceId
     * @param gray
     * @return
     */
    ServiceInstance choose(String serviceId,boolean gray);

}
