package com.viettel.vocs.microchassis.metrics;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

public class ObserveMetric extends PrometheusMetric {
    DoubleAdder sum = new DoubleAdder();
    AtomicLong counter = new AtomicLong();
    long lastCounter = 0;
    double lastSum = 0;

    @Override
    public void doCollect(double value) {
        long l = counter.incrementAndGet();
        sum.add(value);
        if (l >= Long.MAX_VALUE - 10000) {
            reset();
        }
    }

    @Override
    public double lastValue() {
        double v = sum.doubleValue();
        long l = counter.get();
        if (l == 0) {
            return 0d;
        }
        double v1 = (v - lastSum) / Math.max(l - lastCounter, 0.1);
        lastCounter = l;
        lastSum = v;
        return v1;
    }

    @Override
    public void reset() {
        counter.set(0);
        sum.reset();
        lastSum = 0;
        lastCounter = 0;
    }

    protected ObserveMetric(String name, Pair<String, String>... labels) {
        super(name, labels);
    }

    protected ObserveMetric(String name, Map<String, String> mapLabels) {
        super(name, mapLabels);
    }
}
