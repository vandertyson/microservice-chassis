package com.viettel.vocs.microchassis.metrics;

public abstract class SimpleMetricEventHandler implements MetricEventHandler {
	@Override
	public void onMetricCache() {
/**
 * Don't need to handler this notification
 */
	}

	@Override
	public void onMetricFetch() {
/**
 * Don't need to handler this notification
 */
	}

	@Override
	public void onBeforeCache() {
		onBeforeCache(MetricCollector.getMetric());
	}
	public void onBeforeCache(Metric metric) {

	}
}
