package com.viettel.vocs.microchassis.tracing.model;

import java.util.function.Supplier;

public interface TracingData {

    String getServiceType();

    void setServiceType(String serviceType);

    RequestType getRequestType();

    void setRequestType(RequestType requestType);

    String getMsisdn();

    void setMsisdn(String msisdn);

    String getSessionID();

    void setSessionID(String sessionID);

    void start(ITracingOperation operation);

    void start(ITracingOperation operation, ITracingOperation parent);

    void start(ITracingOperation operation, String parentContextID);

    void tag(ITracingOperation operation, String key, String value);

    void log(ITracingOperation operation, String key, Supplier<String> value);

    void log(ITracingOperation operation, String key, String value);

    void finish(ITracingOperation operation);

    String getContextID(ITracingOperation operation);
}
