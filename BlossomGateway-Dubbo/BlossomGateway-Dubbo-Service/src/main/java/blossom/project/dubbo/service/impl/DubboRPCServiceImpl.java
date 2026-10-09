package blossom.project.dubbo.service.impl;

import blossom.project.client.api.ApiProtocol;
import blossom.project.client.api.ApiService;
import blossom.project.dubbo.service.DubboRPCService;
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
