package com.viettel.vocs.microchassis.tracing.model;

import com.viettel.vocs.microchassis.tracing.TracingConfiguration;
import com.viettel.vocs.common.file.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTracingConfiguration {
    @Test
    public void testTracingConfiguration() {
        TracingConfiguration tracingConfiguration = new TracingConfiguration();

        tracingConfiguration.setEnableTracing(true);

        Set<String> msisdn = new HashSet<>();
        msisdn.add("0396100896");
        msisdn.add("0366100566");

        Set<String> sessionList = new HashSet<>();
        sessionList.add("ss-0316594123");
        sessionList.add("ss-0316545571");

        tracingConfiguration.setListMsisdn(msisdn);
        tracingConfiguration.setListSessionId(sessionList);

        String expected = JsonUtils.getEncoder().toJson(tracingConfiguration);
        System.out.println(expected);

        // decoding steps
        // Step 1: Call constructor
        // Step 2: Use reflection to set field value
        TracingConfiguration parameter1 = JsonUtils.getDecoder().fromJson(expected, TracingConfiguration.class);
        String actual = JsonUtils.getEncoder().toJson(parameter1);

        assertEquals(expected, actual);
    }
}
