package blossom.project.httpserver.controller;

import blossom.project.client.api.ApiProperties;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class HttpControllerTest {
    @Test
    public void configuredInstanceHeaderPreservesExistingResponses() throws Exception {
        HttpController controller = new HttpController();
        ReflectionTestUtils.setField(controller, "apiProperties", new ApiProperties());
        ReflectionTestUtils.setField(controller, "instanceId", "A");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        String[][] cases = {{"/http-server/ping", "this is application1"},
                {"/http-server/ping2", "this is ping1"}};
        for (String[] testCase : cases) {
            MvcResult result = mvc.perform(get(testCase[0])).andReturn();
            assertEquals(200, result.getResponse().getStatus());
            assertEquals(testCase[1], result.getResponse().getContentAsString());
            assertEquals("A", result.getResponse().getHeader("X-Service-Instance"));
        }
    }
}
