package com.viettel.vocs.microchassis.tracing.model;

import com.viettel.vocs.microchassis.base.ChassisConfig;

public class TraceSessionHistory {
    private long lastTrace;
    private long start;

    private long idleSec = ChassisConfig.TracingConfig.MAX_TRACING_IDLE_SEC.get();

    public long getIdleSec() {
        return idleSec;
    }

    public void setIdleSec(long idleSec) {
        this.idleSec = idleSec;
    }

    public TraceSessionHistory() {
    }

    public long getLastTrace() {
        return lastTrace;
    }

    public void setLastTrace(long lastTrace) {
        this.lastTrace = lastTrace;
    }

    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }
}
