package io.aegisflow.httpserver.controller;

import io.aegisflow.client.api.ApiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class FaultScenarioTest {
    private HttpController controller;
    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setup() {
        controller = new HttpController();
        ReflectionTestUtils.setField(controller, "apiProperties", new ApiProperties());
        ReflectionTestUtils.setField(controller, "instanceId", "A");
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void faultEndpointsAreDisabledByDefault() throws Exception {
        for (String path : new String[]{"slow", "error", "task-count"}) {
            assertEquals(404, mvc.perform(get("/http-server/" + path)).andReturn().getResponse().getStatus());
        }
        assertEquals(404, mvc.perform(post("/http-server/tasks").contentType("application/json")
                .content("{\"requestId\":\"same-id\"}")).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/http-server/ping")).andReturn().getResponse().getStatus());
    }

    private void enable() { ReflectionTestUtils.setField(controller, "faultsEnabled", true); }

    @Test
    public void delayIsBoundedAndEchoed() throws Exception {
        enable();
        for (String delay : new String[]{"-1", "5001", "abc"}) {
            assertEquals(400, mvc.perform(get("/http-server/slow").param("delayMs", delay)).andReturn().getResponse().getStatus());
        }
        var response = mvc.perform(get("/http-server/slow").param("delayMs", "0")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(0, mapper.readTree(response.getContentAsString()).get("delayMs").asInt());
        assertEquals("A", response.getHeader("X-Service-Instance"));
    }

    @Test
    public void intentionalErrorHasStableStatusAndBody() throws Exception {
        enable();
        var response = mvc.perform(get("/http-server/error")).andReturn().getResponse();
        assertEquals(503, response.getStatus());
        assertEquals("intentional-test-failure", mapper.readTree(response.getContentAsString()).get("error").asText());
    }

    @Test
    public void repeatedWriteIsCountedAndReadDoesNotMutate() throws Exception {
        enable();
        for (int n = 1; n <= 2; n++) {
            var response = mvc.perform(post("/http-server/tasks").contentType("application/json")
                    .content("{\"requestId\":\"same-id\"}")).andReturn().getResponse();
            assertEquals(200, response.getStatus());
            JsonNode result = mapper.readTree(response.getContentAsString());
            assertEquals("same-id", result.get("requestId").asText());
            assertEquals(n, result.get("executionNumber").asInt());
        }
        for (int n = 0; n < 2; n++) {
            var response = mvc.perform(get("/http-server/task-count")).andReturn().getResponse();
            assertEquals(2, mapper.readTree(response.getContentAsString()).get("executions").asInt());
        }
    }

    @Test
    public void invalidTaskDoesNotIncrementCounter() throws Exception {
        enable();
        for (String body : new String[]{"{}", "{\"requestId\":\"\"}", "{\"requestId\":\"bad id\"}", "not-json"}) {
            assertEquals(400, mvc.perform(post("/http-server/tasks").contentType("application/json")
                    .content(body)).andReturn().getResponse().getStatus());
        }
        var response = mvc.perform(get("/http-server/task-count")).andReturn().getResponse();
        assertEquals(0, mapper.readTree(response.getContentAsString()).get("executions").asInt());
    }

    @Test
    public void invalidResponseDelayDoesNotExecuteTask() throws Exception {
        enable();
        assertEquals(400, mvc.perform(post("/http-server/tasks").param("responseDelayMs", "5001")
                .contentType("application/json").content("{\"requestId\":\"delay-test\"}"))
                .andReturn().getResponse().getStatus());
        var count = mvc.perform(get("/http-server/task-count")).andReturn().getResponse();
        assertEquals(0, mapper.readTree(count.getContentAsString()).get("executions").asInt());
    }
}
