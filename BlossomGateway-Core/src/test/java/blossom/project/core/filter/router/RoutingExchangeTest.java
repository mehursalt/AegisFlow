package blossom.project.core.filter.router;

import blossom.project.common.config.Rule;
import blossom.project.core.context.GatewayContext;
import blossom.project.core.request.GatewayRequest;
import blossom.project.core.helper.ResponseHelper;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;
import org.junit.Test;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import static org.junit.Assert.*;

public class RoutingExchangeTest {
    private static Response response(int status) {
        return (Response) Proxy.newProxyInstance(Response.class.getClassLoader(), new Class[]{Response.class}, (proxy, method, args) -> {
            switch(method.getName()) {
                case "getStatusCode": return status;
                case "getHeaders": return new DefaultHttpHeaders();
                case "getResponseBodyAsBytes": return "ok".getBytes(StandardCharsets.UTF_8);
                default: return null;
            }
        });
    }
    private static final class Fixture implements AutoCloseable {
        final EmbeddedChannel channel;
        final FullHttpRequest original;
        final GatewayContext context;
        Fixture(HttpMethod method, int retries, int totalMs) {
            ChannelInboundHandlerAdapter handler = new ChannelInboundHandlerAdapter();
            channel = new EmbeddedChannel(handler);
            original = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, "/test", Unpooled.copiedBuffer("replay-body", StandardCharsets.UTF_8));
            original.headers().set("X-Request-Id", "route-test");
            GatewayRequest request = new GatewayRequest("backend:1", StandardCharsets.UTF_8, "127.0.0.1", "localhost", "/test", method, "application/json", original.headers(), original);
            request.setModifyHost("127.0.0.1:9999");
            Rule rule = new Rule();
            rule.setId("test");
            rule.getRetryConfig().setTimes(retries);
            rule.getRetryConfig().setTotalTimeoutMs(totalMs);
            rule.getRetryConfig().setBackoffMs(1);
            context = new GatewayContext("http", channel.pipeline().context(handler), true, request, rule, 0);
        }
        void start(Function<Request, CompletableFuture<Response>> sender) { new RoutingExchange(context, 1000, null, sender).start(); }
        FullHttpResponse waitResponse() throws Exception {
            for(int n=0;n<250;n++) {
                channel.runPendingTasks(); channel.runScheduledPendingTasks();
                FullHttpResponse out = channel.readOutbound();
                if(out != null) { return out; }
                Thread.sleep(2);
            }
            throw new AssertionError("Response did not finish");
        }
        public void close() {
            channel.finishAndReleaseAll();
            if(original.refCnt()>0) { original.release(); }
        }
    }

    @Test public void zeroRetriesMeansOneAttempt() throws Exception {
        try(Fixture f=new Fixture(HttpMethod.GET,0,1000)) {
            List<Request> calls=new ArrayList<>();
            f.start(request -> { calls.add(request); return CompletableFuture.failedFuture(new IOException("connection failed")); });
            var out=f.waitResponse();
            assertEquals(500,out.status().code()); out.release();
            assertEquals(1,calls.size()); assertEquals(0,f.original.refCnt());
        }
    }

    @Test public void queryRetryKeepsBodyAliveAndDoesNotDuplicateHeaders() throws Exception {
        try(Fixture f=new Fixture(HttpMethod.GET,2,1000)) {
            List<Request> calls=new ArrayList<>();
            f.start(request -> {
                assertEquals(1,f.original.refCnt());
                assertArrayEquals("replay-body".getBytes(StandardCharsets.UTF_8), request.getByteData());
                assertEquals(1,request.getHeaders().getAll("userId").size());
                calls.add(request);
                return calls.size()<3 ? CompletableFuture.failedFuture(new CompletionException(new IOException("retry"))) : CompletableFuture.completedFuture(response(200));
            });
            var out=f.waitResponse(); assertEquals(200,out.status().code()); out.release();
            assertEquals(3,calls.size()); assertEquals(0,f.original.refCnt());
        }
    }

    @Test public void writesAreNotAutomaticallyRetried() throws Exception {
        try(Fixture f=new Fixture(HttpMethod.POST,3,1000)) {
            List<Request> calls=new ArrayList<>();
            f.start(request -> { calls.add(request); return CompletableFuture.failedFuture(new IOException("unknown write outcome")); });
            var out=f.waitResponse(); out.release();
            assertEquals(1,calls.size()); assertEquals(0,f.original.refCnt());
        }
    }

    @Test public void http503IsNotBlindlyRetried() throws Exception {
        try(Fixture f=new Fixture(HttpMethod.GET,3,1000)) {
            List<Request> calls=new ArrayList<>();
            f.start(request -> { calls.add(request); return CompletableFuture.completedFuture(response(503)); });
            var out=f.waitResponse(); assertEquals(503,out.status().code()); out.release();
            assertEquals(1,calls.size());
        }
    }

    @Test public void totalDeadlineCancelsPendingAttempt() throws Exception {
        try(Fixture f=new Fixture(HttpMethod.GET,3,40)) {
            CompletableFuture<Response> pending=new CompletableFuture<>();
            f.start(request -> pending);
            var out=f.waitResponse(); assertEquals(504,out.status().code()); out.release();
            assertTrue(pending.isCancelled()); assertEquals(0,f.original.refCnt());
        }
    }

    @Test public void lateResponseCannotWriteTwice() throws Exception {
        try(Fixture f=new Fixture(HttpMethod.GET,0,40)) {
            CompletableFuture<Response> pending=new CompletableFuture<>() { @Override public boolean cancel(boolean interrupt) { return false; } };
            f.start(request -> pending);
            var out=f.waitResponse(); assertEquals(504,out.status().code()); out.release();
            pending.complete(response(200));
            f.channel.runPendingTasks();
            assertNull(f.channel.readOutbound());
            f.context.written(); ResponseHelper.writeResponse(f.context);
            assertNull(f.channel.readOutbound()); assertEquals(0,f.original.refCnt());
        }
    }

    @Test public void disconnectCancelsAndReleasesWithoutAResponse() {
        try(Fixture f=new Fixture(HttpMethod.GET,3,1000)) {
            CompletableFuture<Response> pending=new CompletableFuture<>();
            f.start(request -> pending); f.channel.close(); f.channel.runPendingTasks();
            assertTrue(pending.isCancelled()); assertEquals(0,f.original.refCnt());
            assertNull(f.channel.readOutbound());
        }
    }
}
