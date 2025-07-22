package com.viettel.vocs.microchassis.metrics;

import com.viettel.vocs.common.os.TimeUtils;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

class ChassisMetricsExporter implements MetricExporter {
	private Collection<MetricData> snapMetrics = null;

	@Override
	public CompletableResultCode export(Collection<MetricData> metrics) {
		snapMetrics = metrics;
		metrics.forEach(metric -> System.out.printf("%s = %s\n", metric.getName(), metric.getData()));
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
	public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) { // converters
		return AggregationTemporality.CUMULATIVE;
	}
}

public class Otel {
	public static void main(String[] args) {
		try {
			PeriodicMetricReader reader = PeriodicMetricReader.builder(new ChassisMetricsExporter()).setInterval(1, TimeUnit.SECONDS).build();
			SdkMeterProvider meterProvider = SdkMeterProvider.builder().setResource(Resource.getDefault()).registerMetricReader(reader).build();
			OpenTelemetrySdk.builder().setMeterProvider(meterProvider).buildAndRegisterGlobal(); // Register the MeterProvider globally

			GlobalOpenTelemetry.get(); // Automatically registers JVM metrics.
//		RuntimeMetrics.registerObservers();
//		RuntimeMetrics.registerObservers(GlobalOpenTelemetry.getMeterProvider());
			Meter meter = GlobalOpenTelemetry.getMeter("example-metrics");

			// Example: Create a simple metric
			LongCounter counter = meter.counterBuilder("example.counter").setDescription("A simple counter").setUnit("1").build();
			counter.add(1);
//			DoubleGauge memGauge = meter.gaugeBuilder("jvm.memory.used").setDescription("Used JVM memory").setUnit("bytes").build();// (measurement -> {

// some shit
			// });
			System.out.println("All set");
			TimeUtils.waitSafeSec(100000);
		} catch (Throwable e){
			System.out.println(e.getMessage());
		}
	}
}