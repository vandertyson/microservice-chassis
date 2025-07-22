package com.viettel.vocs.microchassis.metrics;

public interface JvmMetricImporter {
    default Metric importJvmMetrics(Metric metric) {
        return null;
    }
}
