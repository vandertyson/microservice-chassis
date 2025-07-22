package com.viettel.vocs.mano.model;

import com.viettel.vocs.common.CommonConfig;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricPushBody {
    @Getter @Setter private String metricName;
    @Getter @Setter private String exporterType = "CNF_SERVICE_EXPORTER";
    @Getter @Setter private String managedObjectType = "CNF_OBJECT";
    @Getter @Setter private String exporterVersion = "v1.0";
    @Getter @Setter private String objectInstanceId = CommonConfig.InstanceInfo.VNF_INSTANCE_ID.get();
    @Getter @Setter private String subObjectInstanceId = CommonConfig.InstanceInfo.VNFC_ID.get();
    @Getter @Setter private Map<String, String> metadata = new HashMap<>();
    @Getter @Setter private List<Double> values = new ArrayList<>();
    @Getter @Setter private List<Long> timestamps = new ArrayList<>();
    @Getter @Setter private transient int pushIndex;
    @Getter @Setter private transient int internalHash;

    public void valueAtTimestamp(double value, long timestamp) {
        this.values.add(value);
        this.timestamps.add(timestamp);
    }

    public MetricPushBody cloneMetaData() {
        MetricPushBody cloned = new MetricPushBody();
        cloned.setMetadata(this.metadata);
        return cloned;
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(metricName);
        if (metadata != null && !metadata.isEmpty()) {
            sb.append("{");
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                sb.append(entry.getKey())
                  .append("=")
                  .append("\"")
                  .append(entry.getValue())
                  .append("\",");
            }
            sb.setLength(sb.length() - 1);
            sb.append("}");
        }
        int size = values.size();
        if (size > 0) {
            sb.append(" ")
                    .append(values.get(size - 1))
                    .append(" ")
                    .append(timestamps.get(size - 1));
        }
        return sb.toString();
    }
}
