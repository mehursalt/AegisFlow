package blossom.project.core.netty.processor;
import blossom.project.core.context.HttpRequestWrapper;
import blossom.project.core.disruptor.ParallelQueue;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class QueueRejectionTest {
    @Test public void fullQueueRejectsAndReleasesRequest() {
        ParallelQueue<HttpRequestWrapper> queue=new ParallelQueue<>() {
            public void add(HttpRequestWrapper e) { throw new AssertionError("Blocking add must not be called"); }
            public void add(HttpRequestWrapper... e) { throw new AssertionError(); }
            public boolean tryAdd(HttpRequestWrapper e) { return false; }
            public boolean tryAdd(HttpRequestWrapper... e) { return false; }
            public void start() {} public void shutDown() {} public boolean isShutDown() { return false; }
        };
        ChannelInboundHandlerAdapter handler=new ChannelInboundHandlerAdapter();
        EmbeddedChannel channel=new EmbeddedChannel(handler);
        FullHttpRequest request=new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.GET,"/test",Unpooled.buffer(1));
        HttpRequestWrapper wrapper=new HttpRequestWrapper(); wrapper.setRequest(request); wrapper.setCtx(channel.pipeline().context(handler));
        new DisruptorNettyCoreProcessor(queue).process(wrapper);
        FullHttpResponse response=channel.readOutbound();
        assertEquals(503,response.status().code()); assertEquals("1",response.headers().get("Retry-After"));
        assertEquals(0,request.refCnt()); response.release(); channel.finishAndReleaseAll();
    }
}
