package io.aegisflow.rpc.core.proxy.jdk;

/**
 * JdkRpcService接口
 */
public interface JdkRpcService {
    /**
     * 当前方法用于测试JDK的代理然后实现rpc调用是否可行
     * @param msg
     * @return
     */
    String testJdkRpc(String msg);
}
