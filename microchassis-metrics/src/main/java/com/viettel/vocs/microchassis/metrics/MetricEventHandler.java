package com.viettel.vocs.microchassis.metrics;

public interface MetricEventHandler {
    /**
     * Trigger sau khi metric được cache thành công (theo biến env METRIC_CACHE_INTERVAL_SEC mặc định là 1s)
     */
    void onMetricCache();

    /**
     * Trigger khi API {@link Metric#getMetricResponse()} hoặc {@link Metric#getAllCachedData()} được gọi
     */
    void onMetricFetch();
    /**
     * Trigger trước khi metric collector cache tất cả các metric đã đăng ký (theo biến env METRIC_CACHE_INTERVAL_SEC mặc định là 1s)
     */
    void onBeforeCache();
}

