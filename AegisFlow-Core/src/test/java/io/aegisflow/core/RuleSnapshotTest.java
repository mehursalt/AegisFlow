package io.aegisflow.core;
import io.aegisflow.common.config.Rule;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RuleSnapshotTest {
    private Rule rule(String id) {
        Rule rule=new Rule(); rule.setId(id); rule.setServiceId("stage7-test");
        rule.setProtocol("http"); rule.setPrefix("/test"); rule.setPaths(new ArrayList<>(List.of("/test/ping")));
        return rule;
    }
    @Test public void invalidUpdateKeepsOldSnapshot() {
        var manager=DynamicConfigManager.getInstance(); manager.putAllRule(List.of(rule("old")));
        Rule invalid=rule("bad"); invalid.setMaxConcurrentRequests(-1);
        try { manager.putAllRule(List.of(invalid)); fail(); } catch(IllegalArgumentException expected) { }
        assertEquals("old",manager.resolveRule("stage7-test","/test/ping").getId());
    }
    @Test public void duplicatePathRejectedAsWholeBatch() {
        try { DynamicConfigManager.getInstance().putAllRule(List.of(rule("a"),rule("b"))); fail(); }
        catch(IllegalArgumentException expected) { }
    }
    @Test public void callerCannotMutatePublishedCollections() {
        Rule original=rule("a"); var manager=DynamicConfigManager.getInstance(); manager.putAllRule(List.of(original));
        original.setPrefix("/changed"); original.getPaths().clear();
        assertEquals("a",manager.resolveRule("stage7-test","/test/ping").getId());
        assertNotNull(manager.resolveRule("stage7-test","/test/extra"));
    }
    @Test public void explicitEmptyArrayClearsAndUnknownServiceReturnsNull() {
        var manager=DynamicConfigManager.getInstance(); manager.putAllRule(List.of());
        assertNull(manager.resolveRule("missing","/test"));
    }
    @Test public void unknownFilterRejected() {
        Rule original=rule("a"); Rule.FilterConfig filter=new Rule.FilterConfig(); filter.setId("typo"); original.addFilterConfig(filter);
        try { DynamicConfigManager.getInstance().putAllRule(List.of(original)); fail(); } catch(IllegalArgumentException expected) { }
    }
    @Test public void singleRuleMutationsRebuildAllIndexes() {
        var manager=DynamicConfigManager.getInstance(); manager.putAllRule(List.of());
        manager.putRule("one",rule("one"));
        assertEquals("one",manager.resolveRule("stage7-test","/test/ping").getId());
        manager.removeRule("one"); assertNull(manager.resolveRule("stage7-test","/test/ping"));
    }
    @Test public void concurrentLookupNeverSeesMixedIndexes() throws Exception {
        var manager=DynamicConfigManager.getInstance();
        Rule a=rule("a");a.setPrefix("/a");
        Rule b=rule("b");b.setPaths(List.of("/b/ping"));
        manager.putAllRule(List.of(a));
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread reader=new Thread(()->{
            try { for(int n=0;n<10000;n++) assertNotNull(manager.resolveRule("stage7-test","/test/ping")); }
            catch(Throwable error){failure.set(error);}
        });
        reader.start();
        for(int n=0;n<1000;n++) manager.putAllRule(List.of(n%2==0?b:a));
        reader.join(); if(failure.get()!=null) throw new AssertionError(failure.get());
    }
}
