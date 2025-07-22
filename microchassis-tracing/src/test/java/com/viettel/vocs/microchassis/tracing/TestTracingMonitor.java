package com.viettel.vocs.microchassis.tracing;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.tracing.client.TracingMonitor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TestTracingMonitor {
    @Test
    public void testTraceByMsisdn() {

    }

    @Test
    public void testTraceBySessionId() {

    }

    @Test
    public void testCheckTracing() {

    }

    @Test
    public void testAutoTimeoutTracing() throws Exception {
        long timeoutSec = 40L;
        TracingMonitor.getInstance().startTracing();
        TracingConfiguration configuration = new TracingConfiguration();
        configuration.setEnableTracing(true);
        configuration.setMaxTracingIdleSec(timeoutSec - 10);
        Set<String> objects = new HashSet<>();
        objects.add("039854");
        configuration.setListMsisdn(objects);
        TracingMonitor.getInstance().updateParameter(configuration);
        try {
            TimeUtils.waitSafeMili(timeoutSec * 1000);
            assertEquals(false, TracingMonitor.getInstance().checkTracing("039854"));
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    public void testSet() {
        Set<String> x = new HashSet<>();
        x.add("1");
        x.add("2");
        boolean add = x.add("1");
        Assertions.assertTrue(!add);
        Assertions.assertEquals(2, x.size());
        boolean remove = x.remove("3");
        Assertions.assertTrue(!remove);
        boolean remove1 = x.remove("2");
        Assertions.assertTrue(remove1);
        Assertions.assertEquals(1, x.size());
    }
}
