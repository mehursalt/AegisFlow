package blossom.project.httpserver.controller;

import blossom.project.client.api.ApiInvoker;
import blossom.project.client.api.ApiProperties;
import blossom.project.client.api.ApiProtocol;
import blossom.project.client.api.ApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HttpController类
 */

@Slf4j
@RestController
@ApiService(serviceId = "backend-http-server", protocol = ApiProtocol.HTTP, patternPath = "/http-server/**")
public class HttpController {
    @Autowired
    private ApiProperties apiProperties;

    @Value("${demo.instance-id:application1}")
    private String instanceId;

    @Value("${demo.faults-enabled:false}")
    private boolean faultsEnabled;

    private final AtomicLong taskExecutions = new AtomicLong();

    private void requireFaultsEnabled() {
        if (!faultsEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @ApiInvoker(path = "/http-server/slow")
    @GetMapping("/http-server/slow")
    public Map<String, Object> slow(@RequestParam(defaultValue = "1000") int delayMs,
                                    HttpServletResponse response) {
        requireFaultsEnabled();
        if (delayMs < 0 || delayMs > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "delayMs must be between 0 and 5000");
        }
        response.setHeader("X-Service-Instance", instanceId);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Test delay interrupted");
        }
        return Map.of("instanceId", instanceId, "delayMs", delayMs);
    }

    @ApiInvoker(path = "/http-server/error")
    @GetMapping("/http-server/error")
    public ResponseEntity<Map<String, Object>> error() {
        requireFaultsEnabled();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("X-Service-Instance", instanceId)
                .body(Map.of("instanceId", instanceId, "error", "intentional-test-failure"));
    }

    public static class TaskRequest {
        private String requestId;
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
    }

    @ApiInvoker(path = "/http-server/tasks")
    @PostMapping("/http-server/tasks")
    public Map<String, Object> createTask(@RequestBody TaskRequest task,
                                         @RequestParam(defaultValue = "0") int responseDelayMs,
                                         HttpServletResponse response) {
        requireFaultsEnabled();
        if (responseDelayMs < 0 || responseDelayMs > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid responseDelayMs");
        }
        String requestId = task.getRequestId();
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid requestId");
        }
        response.setHeader("X-Service-Instance", instanceId);
        // Deliberately non-idempotent: this counter exposes repeated execution in tests.
        long executionNumber = taskExecutions.incrementAndGet();
        try { Thread.sleep(responseDelayMs); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Test response interrupted");
        }
        return Map.of("instanceId", instanceId, "requestId", requestId, "executionNumber", executionNumber);
    }

    @ApiInvoker(path = "/http-server/task-count")
    @GetMapping("/http-server/task-count")
    public Map<String, Object> taskCount(HttpServletResponse response) {
        requireFaultsEnabled();
        response.setHeader("X-Service-Instance", instanceId);
        return Map.of("instanceId", instanceId, "executions", taskExecutions.get());
    }

    @ApiInvoker(path = "/http-server/ping")
    @GetMapping("/http-server/ping")
    public String ping(HttpServletResponse response) {
        response.setHeader("X-Service-Instance", instanceId);
        log.info("{}", apiProperties);
        try {
            //Thread.sleep(10000000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        //多服务启动的时候，这里改一下，就可以实现集群负载均衡的效果
        return "this is application1";
    }

    @ApiInvoker(path = "/http-server/ping2")
    @GetMapping("/http-server/ping2")
    public String ping2(HttpServletResponse response) {
        response.setHeader("X-Service-Instance", instanceId);
        log.info("{}", apiProperties);
        try {
            //Thread.sleep(10000000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        //多服务启动的时候，这里改一下，就可以实现集群负载均衡的效果
        return "this is ping1";
    }
}
