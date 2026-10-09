package blossom.project.rpc.core.proxy.jdk;

/**
 * JdkRpcServiceImpl类
 */
public class JdkRpcServiceImpl implements JdkRpcService{
    @Override
    public String testJdkRpc(String msg) {
        System.out.println("the msg is : "+msg);
        return "success！！！ \n looks like it could work！！！";
    }
}
