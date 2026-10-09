package io.aegisflow.core.filter.flow;

import io.aegisflow.common.config.Rule;

/**
 * GatewayFlowControlRule接口
 * 网关流控规则接口
 */
public interface GatewayFlowControlRule {

    /**
     * 执行流控规则过滤器
     * @param flowControlConfig
     * @param serviceId
     */
    void doFlowControlFilter(Rule.FlowControlConfig flowControlConfig, String serviceId);
}
