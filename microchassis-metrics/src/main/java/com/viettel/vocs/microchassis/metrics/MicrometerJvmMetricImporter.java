package com.viettel.vocs.microchassis.metrics;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.apache.commons.lang3.tuple.Pair;

import java.util.stream.Collectors;

public class MicrometerJvmMetricImporter implements JvmMetricImporter {

	@Override
	public Metric importJvmMetrics(Metric metric) {
		PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
		new ProcessorMetrics().bindTo(registry);
		new JvmMemoryMetrics().bindTo(registry);
		new JvmThreadMetrics().bindTo(registry);
		new JvmGcMetrics().bindTo(registry);
		new JvmHeapPressureMetrics().bindTo(registry);
		new JvmThreadDeadlockMetrics().bindTo(registry);
		new JvmCompilationMetrics().bindTo(registry);
		new ClassLoaderMetrics().bindTo(registry);
		Thread collectJVM = new Thread(() -> {
			do {
				try {
					registry.getMeters()
						.forEach(meter -> meter.measure()
							.forEach(measurement -> metric.set(
								meter.getId().getName().replace(".", "_") + "_" + measurement.getStatistic().name().toLowerCase(),
								meter.getId().getType().name(),
								measurement.getValue(),
								meter.getId().getTags().stream().map(tag -> {
									String key = tag.getKey();
									if (key.equalsIgnoreCase(ChassisConfig.MetricConfig.SERIES_TYPE_KEY.get())) {
										key = "sub_type";
									}
									return Pair.of(key, tag.getValue());
								}).collect(Collectors.toList()))));
				} catch (Throwable e){
					JvmMetricImporterFactory.logger.error("Collect metrics JVM failed with error "+ e.getMessage());
				}
			} while (TimeUtils.waitSafeMili(CommonConfig.StatisticConfig.intervalStatistic.get()));
		});
		collectJVM.start();

		return metric;
	}
}
