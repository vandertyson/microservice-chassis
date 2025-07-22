package com.viettel.vocs.microchassis.connection;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public class ServiceStatus {
    private final int port;
    private final Set<String> resolvedIP = new CopyOnWriteArraySet<>();
    private final Map<String, Boolean> connected = new HashMap<>();

    public ServiceStatus(int port) {
        this.port = port;
    }

    public Set<String> getResolvedIP() {
        return resolvedIP;
    }

    public Map<String, Boolean> getConnected() {
        return connected;
    }

    public Integer getPort() {
        return port;
    }
}
