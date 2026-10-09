package blossom.project.core;

import blossom.project.core.request.GatewayRequest;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class RequestBodySnapshotTest {
    private GatewayRequest wrap(FullHttpRequest incoming) {
        GatewayRequest request = new GatewayRequest("backend:1", StandardCharsets.UTF_8,
                "127.0.0.1", "localhost", "/test", HttpMethod.GET, "application/json", incoming.headers(), incoming);
        request.setModifyHost("127.0.0.1:9999");
        return request;
    }

    @Test public void detachedBodyDoesNotChangeWithOrDependOnReleasedBuffer() {
        var incoming = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.GET,"/test",Unpooled.copiedBuffer("original",StandardCharsets.UTF_8));
        GatewayRequest request = wrap(incoming);
        incoming.content().setByte(0,'X');
        incoming.release();
        assertArrayEquals("original".getBytes(StandardCharsets.UTF_8),request.build().getByteData());
        assertEquals("original",request.getBody());
    }

    @Test public void emptyBodyCanBeReadAfterOriginalRelease() {
        var incoming = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.GET,"/test",Unpooled.buffer(1));
        GatewayRequest request = wrap(incoming);
        incoming.release();
        assertEquals(0,incoming.refCnt());
        assertEquals("",request.getBody());
        assertEquals(0,request.build().getByteData().length);
    }
}
