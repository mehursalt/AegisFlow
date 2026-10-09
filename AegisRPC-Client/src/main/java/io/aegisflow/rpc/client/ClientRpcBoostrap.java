package io.aegisflow.rpc.client;

import io.aegisflow.rpc.client.proxy.RpcClientProxyFactory;
import io.aegisflow.rpc.core.service.RpcService;

/**
 * ClientRpcBoostrap类
 * client端的启动类
 */
//@SpringBootApplication
public class ClientRpcBoostrap {
    public static void main(String[] args) {

        RpcClientProxyFactory proxy = new RpcClientProxyFactory();
        RpcService clientProxy = proxy.getClientProxy
                (RpcService.class, "127.0.0.1", 8080);
        System.out.println(clientProxy.testRpcRequest("hello"));


    }
}
