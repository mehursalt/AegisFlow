package blossom.project.httpserver;

import blossom.project.common.utils.RequestIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestTraceFilter.class);
    private final String instanceId;

    public RequestTraceFilter(@Value("${demo.instance-id:application1}") String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        List<String> values = Collections.list(request.getHeaders(RequestIds.HEADER));
        String id = RequestIds.normalize(values.size() == 1 ? values.get(0) : null);
        response.setHeader(RequestIds.HEADER, id);
        request.setAttribute(RequestIds.HEADER, id);
        long started = System.nanoTime();
        boolean returned = false;
        LOG.info("backend_request requestId={} instance={} method={} path={}",
                id, instanceId, request.getMethod(), request.getRequestURI());
        try {
            chain.doFilter(request, response);
            returned = true;
        } finally {
            LOG.info("backend_complete requestId={} instance={} status={} durationMs={}",
                    id, instanceId, returned ? response.getStatus() : 500,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }
}
