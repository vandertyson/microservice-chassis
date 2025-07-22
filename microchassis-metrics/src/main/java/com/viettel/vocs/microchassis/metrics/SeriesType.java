/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.metrics;

/**
 * @author vttek
 */
public enum SeriesType {
    requests("requests", MetricType.ADD),
    responses("responses", MetricType.ADD),
    avgT("avg_latency", MetricType.SET),
    totaltimes("total_times", MetricType.SET),
    maxtime("max_latency", MetricType.SET),
    timeouts("timeouts", MetricType.ADD),
    error2001("error2001", MetricType.ADD),
    error3001("error3001", MetricType.ADD),
    error4001("error4001", MetricType.ADD),
    error4012("error4012", MetricType.ADD),
    under1ms("under1ms", MetricType.ADD),
    under2ms("under2ms", MetricType.ADD),
    under4ms("under4ms", MetricType.ADD),
    under8ms("under8ms", MetricType.ADD),
    under16ms("under16ms", MetricType.ADD),
    over16ms("over16ms", MetricType.ADD),
    dropRequest("drops", MetricType.ADD);

    private final String labelsName;
    private final MetricType type;

    SeriesType(String labelsName, MetricType type) {
        this.labelsName = labelsName;
        this.type = type;
    }

    public String getLabelsName() {
        return labelsName;
    }

    public MetricType getType() {
        return type;
    }

}
