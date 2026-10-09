package io.aegisflow.httpserver.controller;

import io.aegisflow.client.api.ApiProperties;
import io.aegisflow.common.utils.RequestIds;
import io.aegisflow.httpserver.RequestTraceFilter;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class RequestTraceTest {
    @Test
    public void traceHeaderIsPreservedOrGeneratedWithoutChangingBody() throws Exception {
        HttpController controller = new HttpController();
        ReflectionTestUtils.setField(controller, "apiProperties", new ApiProperties());
        ReflectionTestUtils.setField(controller, "instanceId", "A");
        var mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(new RequestTraceFilter("A")).build();
        var known = mvc.perform(get("/http-server/ping").header(RequestIds.HEADER, "trace-123")).andReturn().getResponse();
        assertEquals("trace-123", known.getHeader(RequestIds.HEADER));
        assertEquals("this is application1", known.getContentAsString());
        var generated = mvc.perform(get("/http-server/ping")).andReturn().getResponse().getHeader(RequestIds.HEADER);
        assertNotNull(generated);
        assertEquals(generated, RequestIds.normalize(generated));
        String invalid = "x".repeat(65);
        assertNotEquals(invalid, RequestIds.normalize(invalid));
        assertNotEquals("bad id", RequestIds.normalize("bad id"));
        var duplicate = mvc.perform(get("/http-server/ping").header(RequestIds.HEADER, "one", "two")).andReturn().getResponse().getHeader(RequestIds.HEADER);
        assertNotEquals("one", duplicate);
        assertNotEquals("two", duplicate);
    }
}
