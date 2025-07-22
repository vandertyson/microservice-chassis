package com.viettel.vocs.microchassis.metrics;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.runtimemetrics.*;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class OtelJvmMetricImporter implements JvmMetricImporter{

    public OtelJvmMetricImporter() {

    }

    @Override
    public Metric importJvmMetrics(Metric metric) {
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(PeriodicMetricReader.builder(new MetricExporter() {
                            @Override
                            public CompletableResultCode export(Collection<MetricData> collection) {
                                collection.forEach(metricData -> metricData.getData().getPoints().forEach(pointData -> {
                                    if (pointData instanceof DoublePointData) {
                                        List<Pair<String, String>> labels = new ArrayList<>();
                                        for (Map.Entry<AttributeKey<?>, Object> entry : pointData.getAttributes().asMap().entrySet()) {
                                          String key= entry.getKey().getKey().toString();
                                          if(key.equalsIgnoreCase(ChassisConfig.MetricConfig.SERIES_TYPE_KEY.get())){
                                            key = "sub_type";
                                          }
                                            labels.add(new ImmutablePair<>(key, entry.getValue().toString()));
                                        }
                                        metric.set(metricData.getName().replace(".","_"),
                                                String.valueOf(metricData.getType()),
                                                ((DoublePointData) pointData).getValue(),
                                                labels);
                                    }
                                    if (pointData instanceof LongPointData) {
                                        List<Pair<String, String>> labels = new ArrayList<>();
                                        for (Map.Entry<AttributeKey<?>, Object> entry : pointData.getAttributes().asMap().entrySet()) {
                                            String key= entry.getKey().getKey().toString();
                                            if(key.equalsIgnoreCase(ChassisConfig.MetricConfig.SERIES_TYPE_KEY.get())){
                                              key = "sub_type";
                                            }
                                            labels.add(new ImmutablePair<>(key, entry.getValue().toString()));
                                        }
                                        metric.set(metricData.getName().replace(".","_"),
                                                String.valueOf(metricData.getType()),
                                                ((LongPointData) pointData).getValue(),
                                                labels);
                                    }
                                }));
                                return CompletableResultCode.ofSuccess();
                            }
                            @Override
                            public CompletableResultCode flush() {
                                return CompletableResultCode.ofSuccess();
                            }

                            @Override
                            public CompletableResultCode shutdown() {
                                return CompletableResultCode.ofSuccess();
                            }

                            @Override
                            public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
                                return AggregationTemporality.CUMULATIVE;
                            }
                        })
                        .setInterval(CommonConfig.StatisticConfig.intervalStatistic.get(), TimeUnit.MILLISECONDS)
                        .build()).setResource(Resource.getDefault()) // Export to Prometheus
                .build();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder().setMeterProvider(meterProvider).buildAndRegisterGlobal();

        MemoryPools.registerObservers(openTelemetry);
        BufferPools.registerObservers(openTelemetry);
        Classes.registerObservers(openTelemetry);
        Cpu.registerObservers(openTelemetry);
        Threads.registerObservers(openTelemetry);
        GarbageCollector.registerObservers(openTelemetry);

        return metric;
    }

}
