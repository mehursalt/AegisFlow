package blossom.project.rpc.core.handler;

import blossom.project.rpc.core.codec.RpcDecode;
import blossom.project.rpc.core.codec.RpcEncode;
import blossom.project.rpc.core.constants.RpcCommonConstants;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * NettyRpcServerHandler类
 */
@Slf4j
public class NettyRpcServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        log.info("---The system starts to initialize the <NettyRpcServer>---");
        ch.pipeline().
                addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                        RpcCommonConstants.HEADER_LENGTH-4,
                        4,
                        0,
                        0))
                .addLast(new RpcDecode())
                .addLast(new RpcEncode())
                .addLast(new NettyRpcServerHandler());
    }
}
