package io.aegisflow.dubbo.service.impl;

import io.aegisflow.client.api.ApiProtocol;
import io.aegisflow.client.api.ApiService;
import io.aegisflow.dubbo.service.DubboRPCService;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * DubboServiceImpl类
 */

@ApiService(serviceId = "backend-dubbo-server", protocol = ApiProtocol.DUBBO,
        patternPath = "/**")
@DubboService
public class DubboRPCServiceImpl implements DubboRPCService {
    @Override
    public String testDubboRPC(String msg) {
        return "hello dubbo!!!"+ msg;
    }
}
