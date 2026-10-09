package io.aegisflow.access;

import java.util.UUID;
import java.util.Set;

/** Versioned facts only: deliberately excludes headers, cookies, bodies and raw URI. */
public record AccessEvent(int schemaVersion, String eventId, String requestId, String gatewayId,
                          String routeId, String serviceId, String instanceId, String method,
                          int status, String outcome, String reason, long durationMs, long completedAtMs) {
    private static final Set<String> METHODS=Set.of("GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS","TRACE","CONNECT","OTHER");
    private static final Set<String> OUTCOMES=Set.of("success","client_error","server_error","write_error");
    private static final Set<String> REASONS=Set.of("http","timeout","gateway_error","limited","overloaded","unauthorized");
    private static final java.util.regex.Pattern REQUEST_ID=java.util.regex.Pattern.compile("[A-Za-z0-9._-]{1,64}");
    public AccessEvent {
        if(schemaVersion!=1 || eventId==null || !UUID.fromString(eventId).toString().equals(eventId)) { throw new IllegalArgumentException("Invalid event version/id"); }
        for(String value:new String[]{requestId,gatewayId,routeId,serviceId,instanceId}) {
            if(value==null || value.length()>128 || value.isEmpty()) { throw new IllegalArgumentException("Invalid event field length"); }
        }
        if(method==null || outcome==null || reason==null || !REQUEST_ID.matcher(requestId).matches() || !METHODS.contains(method)
                || (status!=0 && (status<100 || status>599)) || durationMs<0 || completedAtMs<=0
                || !OUTCOMES.contains(outcome) || !REASONS.contains(reason)) {
            throw new IllegalArgumentException("Invalid event values");
        }
    }
}
