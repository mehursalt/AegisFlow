package io.aegisflow.httpserver.controller;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class ReportControllerTest {
    @Test public void reportScenarioIsDisabledUnlessExplicitlyEnabled() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.register(ReportController.class); context.refresh();
            assertTrue(context.getBeansOfType(ReportController.class).isEmpty());
        }
    }
    @Test public void boundedDelayAndInstanceHeaderWork() throws Exception {
        ReportController controller=new ReportController(); ReflectionTestUtils.setField(controller,"instanceId","A");
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        var ok=mvc.perform(get("/report/slow").param("delayMs","0")).andReturn().getResponse();
        assertEquals(200,ok.getStatus()); assertEquals("A",ok.getHeader("X-Service-Instance"));
        assertEquals(400,mvc.perform(get("/report/slow").param("delayMs","5001")).andReturn().getResponse().getStatus());
    }
}
