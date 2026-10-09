package blossom.project.core.helper;

import blossom.project.common.enums.ResponseCode;
import blossom.project.common.utils.RequestIds;
import blossom.project.core.context.GatewayContext;
import blossom.project.core.context.IContext;
import blossom.project.core.monitor.GatewayMetrics;
import blossom.project.core.response.GatewayResponse;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;

public final class ResponseHelper {
    private ResponseHelper() { }
    public static FullHttpResponse getHttpResponse(ResponseCode code) {
        return getHttpResponse(GatewayResponse.buildGatewayResponse(code));
    }
    private static FullHttpResponse getHttpResponse(GatewayResponse response) {
        byte[] content;
        if (response.getFutureResponse() != null) {
            content = response.getFutureResponse().getResponseBodyAsBytes();
        } else {
            content = response.getContent() == null ? new byte[0] : response.getContent().getBytes(StandardCharsets.UTF_8);
        }
        FullHttpResponse http = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                response.getHttpResponseStatus(), Unpooled.wrappedBuffer(content));
        if (response.getFutureResponse() != null) {
            http.headers().add(response.getFutureResponse().getHeaders());
        } else {
            http.headers().add(response.getResponseHeaders());
        }
        http.headers().add(response.getExtraResponseHeaders());
        http.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length);
        http.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
        return http;
    }
    public static void writeResponse(IContext context) {
        context.releaseRequest();
        if (!context.isWritten()) { return; }
        if (context instanceof GatewayContext && !((GatewayContext) context).claimResponse()) { return; }
        FullHttpResponse http = getHttpResponse((GatewayResponse) context.getResponse());
        GatewayMetrics.Observation observation = context instanceof GatewayContext
                ? ((GatewayContext) context).getObservation() : null;
        if (context instanceof GatewayContext) {
            http.headers().set(RequestIds.HEADER, ((GatewayContext) context).getRequest().getHeaders().get(RequestIds.HEADER));
        }
        writeAndObserve(context.getNettyCtx(), http, observation, context.isKeepAlive(), () -> {
            context.completed();
            context.invokeCompletedCallBack();
        });
    }
    public static void writeAndObserve(ChannelHandlerContext ctx, FullHttpResponse http,
                                       GatewayMetrics.Observation observation, boolean keepAlive, Runnable completed) {
        int status = http.status().code();
        if (observation != null) { http.headers().set(RequestIds.HEADER, observation.requestId()); }
        if (keepAlive) { http.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE); }
        try {
            var future = ctx.writeAndFlush(http);
            future.addListener((ChannelFutureListener) write -> {
                if (observation != null) { observation.complete(status, write.isSuccess()); }
                if (completed != null) { completed.run(); }
            });
            if (!keepAlive) { future.addListener(ChannelFutureListener.CLOSE); }
        } catch (RuntimeException e) {
            ReferenceCountUtil.safeRelease(http);
            if (observation != null) { observation.complete(status, false); }
            throw e;
        }
    }
}
