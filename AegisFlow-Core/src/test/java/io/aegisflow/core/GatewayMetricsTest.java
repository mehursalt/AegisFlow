package io.aegisflow.core;

import io.aegisflow.common.enums.ResponseCode;
import io.aegisflow.core.helper.ResponseHelper;
import io.aegisflow.core.monitor.GatewayMetrics;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class GatewayMetricsTest {
    private static class DeferredWrite extends ChannelDuplexHandler {
        ChannelHandlerContext context;
        ChannelPromise promise;
        Object message;
        @Override public void handlerAdded(ChannelHandlerContext ctx) { context = ctx; }
        @Override public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise pending) {
            message = msg;
            promise = pending;
        }
    }

    @Test
    public void timerWaitsForWriteAndRecordsOnlyOnce() throws Exception {
        try (GatewayMetrics metrics = new GatewayMetrics()) {
            DeferredWrite deferred = new DeferredWrite();
            ChannelInboundHandlerAdapter inbound = new ChannelInboundHandlerAdapter();
            EmbeddedChannel channel = new EmbeddedChannel(deferred, inbound);
            var observation = metrics.begin("trace-test", "GET");
            observation.route("test-rule");
            observation.watchDisconnect(channel.closeFuture());
            var response = ResponseHelper.getHttpResponse(ResponseCode.SUCCESS);
            ResponseHelper.writeAndObserve(channel.pipeline().context(inbound), response, observation, true, null);
            assertEquals(1.0, metrics.registry().get("gateway.inflight").gauge().value(), 0.0);
            assertNull(metrics.registry().find("gateway.request").timer());
            Thread.sleep(30);
            deferred.promise.setSuccess();
            channel.runPendingTasks();
            observation.complete(503, false);
            var timer = metrics.registry().get("gateway.request").tags("status", "200", "outcome", "success").timer();
            assertEquals(1, timer.count());
            assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 25);
            assertEquals(0.0, metrics.registry().get("gateway.inflight").gauge().value(), 0.0);
            assertFalse(metrics.registry().scrape().contains("trace-test"));
            response.release();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void writeFailureAndDisconnectReleaseInflight() {
        try (GatewayMetrics metrics = new GatewayMetrics()) {
            DeferredWrite deferred = new DeferredWrite();
            ChannelInboundHandlerAdapter inbound = new ChannelInboundHandlerAdapter();
            EmbeddedChannel channel = new EmbeddedChannel(deferred, inbound);
            var observation = metrics.begin("failed", "GET");
            observation.watchDisconnect(channel.closeFuture());
            var response = ResponseHelper.getHttpResponse(ResponseCode.INTERNAL_ERROR);
            ResponseHelper.writeAndObserve(channel.pipeline().context(inbound), response, observation, true, null);
            deferred.promise.setFailure(new IOException("test write failure"));
            channel.runPendingTasks();
            channel.close();
            assertEquals(1, metrics.registry().get("gateway.request").tags("outcome", "write_error").timer().count());
            assertEquals(0.0, metrics.registry().get("gateway.inflight").gauge().value(), 0.0);
            response.release();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void disconnectBeforeResponseIsCountedOnce() {
        try (GatewayMetrics metrics = new GatewayMetrics()) {
            EmbeddedChannel channel = new EmbeddedChannel();
            var observation = metrics.begin("disconnect", "CUSTOM");
            observation.watchDisconnect(channel.closeFuture());
            channel.close();
            observation.complete(200, true);
            assertEquals(1, metrics.registry().get("gateway.request").tags("status", "0", "method", "OTHER").timer().count());
            assertEquals(0.0, metrics.registry().get("gateway.inflight").gauge().value(), 0.0);
        }
    }

    @Test
    public void earlyResponsesKeepTheirDeclaredStatus() {
        for (ResponseCode code : new ResponseCode[]{ResponseCode.UNAUTHORIZED, ResponseCode.PATH_NO_MATCHED, ResponseCode.REQUEST_TIMEOUT}) {
            var response = ResponseHelper.getHttpResponse(code);
            try { assertEquals(code.getStatus(), response.status()); }
            finally { response.release(); }
        }
    }
}
