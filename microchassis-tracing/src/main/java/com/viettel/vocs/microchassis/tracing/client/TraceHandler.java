package com.viettel.vocs.microchassis.tracing.client;

public interface TraceHandler {
    void onSessionTrace(String msisdn);

    void onMsisdnTrace(String sessionId);
}
