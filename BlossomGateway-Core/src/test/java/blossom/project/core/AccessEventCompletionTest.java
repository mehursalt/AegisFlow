package blossom.project.core;
import blossom.project.core.monitor.GatewayMetrics;
import blossom.project.access.AccessEvent;
import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;
public class AccessEventCompletionTest {
    @Test public void completionEmitsOneEventAndDistinctRequestsHaveDistinctIds() {
        try(GatewayMetrics metrics=new GatewayMetrics()) {
            ArrayList<AccessEvent> events=new ArrayList<>(); metrics.setEventSink("gateway",events::add);
            var first=metrics.begin("same-trace","GET"); first.complete(200,true); first.complete(503,true);
            assertEquals(1,events.size()); metrics.begin("same-trace","GET").complete(200,true);
            assertEquals(2,events.size()); assertNotEquals(events.get(0).eventId(),events.get(1).eventId());
        }
    }
}
