package io.aegisflow.httpserver.controller;

import io.aegisflow.client.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
@ConditionalOnProperty(prefix="demo", name="faults-enabled", havingValue="true")
@ApiService(serviceId="backend-report-server", protocol=ApiProtocol.HTTP, patternPath="/report/**")
public class ReportController {
    @Value("${demo.instance-id:application1}")
    private String instanceId;

    @ApiInvoker(path="/report/slow")
    @GetMapping("/report/slow")
    public Map<String,Object> slow(@RequestParam(defaultValue="1000") int delayMs, HttpServletResponse response) {
        if(delayMs<0 || delayMs>5000) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid delayMs"); }
        response.setHeader("X-Service-Instance",instanceId);
        try { Thread.sleep(delayMs); }
        catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE); }
        return Map.of("instanceId",instanceId,"delayMs",delayMs);
    }
}
