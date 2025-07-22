package com.viettel.vocs.microchassis.metrics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.viettel.vocs.microchassis.base.ChassisConfig.PerformanceConfig.JVM_IMPORTER_CONFIG;

public class JvmMetricImporterFactory implements JvmMetricImporter{
    private JvmMetricImporterFactory() {
    }
    static final Logger logger = LogManager.getLogger(MetricCollector.class);

    public static JvmMetricImporter createJvmMetricImporter() {
        if (JVM_IMPORTER_CONFIG.get().equals("otel")){
            logger.info("using OpenTelemetry as importer");
            return new OtelJvmMetricImporter();
        }
        if (JVM_IMPORTER_CONFIG.get().equals("micrometer")){
            logger.info("using Micrometer as importer");
            return new MicrometerJvmMetricImporter();
        }
        else {
            logger.error("JVM Importer config invalid, creating OpenTelemetry importer by default");
            return new OtelJvmMetricImporter();
        }
    }
}
