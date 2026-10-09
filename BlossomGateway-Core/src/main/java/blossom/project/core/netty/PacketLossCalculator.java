package blossom.project.core.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import java.util.concurrent.atomic.AtomicInteger;
/**
 */

public class PacketLossCalculator {

    private final Channel channel;
    // 发送成功的数据包数量
    private final AtomicInteger successCount = new AtomicInteger(0);

    // 发送失败的数据包数量
    private final AtomicInteger failureCount = new AtomicInteger(0);

    public PacketLossCalculator(Channel channel) {
        this.channel = channel;
    }

    public void sendData(Object data) {
        // 发送数据，并添加监听器
        ChannelFuture future = channel.writeAndFlush(data);
        future.addListener((ChannelFutureListener) future1 -> {
            if (future1.isSuccess()) {
                // 发送成功
                successCount.incrementAndGet();
            } else {
                // 发送失败
                failureCount.incrementAndGet();
            }
        });
    }

    public double calculateLossRate() {
        // 计算丢包率
        int success = successCount.get();
        int failure = failureCount.get();
        int total = success + failure;
        if (total == 0) {
            // 避免除以0的情况
            return 0.0;
        } else {
            return (double) failure / (double) total;
        }
    }
}
