package io.aegisflow.access;
import com.rabbitmq.client.*;
import java.io.IOException;
import java.util.Map;

public final class RabbitTopology {
    public static final String EXCHANGE="gateway.access", QUEUE="gateway.access.events";
    public static final String RETRY="gateway.access.retry", DEAD="gateway.access.failed";
    private RabbitTopology() { }
    public static ConnectionFactory factory(String host,int port,String user,String password) {
        ConnectionFactory factory=new ConnectionFactory();
        factory.setHost(host); factory.setPort(port); factory.setUsername(user); factory.setPassword(password);
        factory.setConnectionTimeout(1000); factory.setHandshakeTimeout(1500); factory.setChannelRpcTimeout(1500);
        factory.setRequestedHeartbeat(5); factory.setAutomaticRecoveryEnabled(false);
        return factory;
    }
    public static void declare(Channel channel) throws IOException {
        channel.exchangeDeclare(EXCHANGE,"direct",true);
        channel.queueDeclare(DEAD,true,false,false,Map.of("x-max-length",2000,"x-overflow","reject-publish"));
        channel.queueBind(DEAD,EXCHANGE,"failed");
        channel.queueDeclare(QUEUE,true,false,false,Map.of("x-max-length",10000,"x-overflow","reject-publish"));
        channel.queueBind(QUEUE,EXCHANGE,"event");
        channel.queueDeclare(RETRY,true,false,false,Map.of("x-message-ttl",1000,"x-dead-letter-exchange",EXCHANGE,"x-dead-letter-routing-key","event","x-max-length",10000,"x-overflow","reject-publish"));
        channel.queueBind(RETRY,EXCHANGE,"retry");
        channel.confirmSelect();
    }
}
