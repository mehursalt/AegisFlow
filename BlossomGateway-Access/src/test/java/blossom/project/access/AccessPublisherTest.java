package blossom.project.access;
import org.junit.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
public class AccessPublisherTest {
    private AccessEvent event() { return new AccessEvent(1,UUID.randomUUID().toString(),"trace-1","gateway","route","service","instance","GET",200,"success","http",12,System.currentTimeMillis()); }
    @Test public void blockedBrokerDoesNotBlockOfferAndBufferIsBounded() throws Exception {
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        AccessPublisher.Transport transport=new AccessPublisher.Transport() {
            public void publish(List<AccessEvent> events) throws Exception { entered.countDown(); release.await(); }
            public void close() { release.countDown(); }
        };
        try(AccessPublisher publisher=new AccessPublisher(2,transport)) {
            publisher.start(); publisher.offer(event()); assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertTrue(publisher.offer(event())); assertTrue(publisher.offer(event()));
            long start=System.nanoTime(); assertFalse(publisher.offer(event()));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<100);
            assertEquals(1,publisher.dropped.get()); release.countDown();
            for(int n=0;n<100 && publisher.published.get()<3;n++) { Thread.sleep(10); }
            assertEquals(3,publisher.published.get());
        }
    }
    @Test public void failedPublishHasFiniteAttemptsAndPreservesEventId() throws Exception {
        List<String> ids=new CopyOnWriteArrayList<>();
        AccessPublisher.Transport transport=new AccessPublisher.Transport() {
            public void publish(List<AccessEvent> events) throws Exception { ids.add(events.get(0).eventId()); throw new java.io.IOException("offline"); }
            public void close() { }
        };
        try(AccessPublisher publisher=new AccessPublisher(2,transport)) {
            publisher.start(); AccessEvent event=event(); publisher.offer(event);
            for(int n=0;n<200 && publisher.dropped.get()==0;n++) { Thread.sleep(10); }
            assertEquals(3,ids.size()); assertTrue(ids.stream().allMatch(id->id.equals(event.eventId())));
            assertEquals(1,publisher.dropped.get());
        }
    }
    @Test public void versionedEventRoundTripsWithOnlyAllowedFields() throws Exception {
        ObjectMapper mapper=new ObjectMapper(); AccessEvent event=event(); String json=mapper.writeValueAsString(event);
        assertEquals(event,mapper.readValue(json,AccessEvent.class));
        Set<String> fields=new HashSet<>(); mapper.readTree(json).fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("schemaVersion","eventId","requestId","gatewayId","routeId","serviceId","instanceId","method","status","outcome","reason","durationMs","completedAtMs"),fields);
    }
    @Test(expected=IllegalArgumentException.class) public void invalidStatusRejected() {
        new AccessEvent(1,UUID.randomUUID().toString(),"trace","gateway","route","service","instance","GET",700,"success","http",0,System.currentTimeMillis());
    }
    @Test public void gracefulCloseFlushesBufferedEvents() {
        AccessPublisher.Transport transport=new AccessPublisher.Transport(){
            public void publish(List<AccessEvent> events){}
            public void close(){}
        };
        AccessPublisher publisher=new AccessPublisher(16,transport);publisher.start();
        for(int n=0;n<8;n++)assertTrue(publisher.offer(event()));
        publisher.drainAndClose(1000);
        assertEquals(8,publisher.published.get());assertEquals(0,publisher.dropped.get());
        assertFalse(publisher.offer(event()));
    }
}
