package com.viettel.vocs.microchassis.connection.client;

import com.viettel.vocs.microchassis.connection.ServiceStatus;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientStatus {
    private final String id;
    private final Map<String, ServiceStatus> serviceStatus = new HashMap<>();

    private Map<String, List<String>> resolvedIP;

    public Map<String, List<String>> getResolvedIP() {
        return resolvedIP;
    }

    public void setResolvedIP(Map<String, List<String>> resolvedIP) {
        this.resolvedIP = resolvedIP;
    }

    public ClientStatus(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Map<String, ServiceStatus> getServiceStatus() {
        return serviceStatus;
    }

    public boolean isHealthy() {
        if (serviceStatus.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, ServiceStatus> entry : serviceStatus.entrySet()) {
            ServiceStatus value = entry.getValue();
            long countConnected = value.getConnected().values().stream().filter(f -> f).count();
            if (countConnected > 0) {
                return true;
            }
        }
        return false;
    }
}
