/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.metrics;

import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;



/**
 * @author vttek
 */
public class AddMetric extends PrometheusMetric {

    private final DoubleAdder adder = new DoubleAdder();
    private final AtomicLong counter = new AtomicLong();

    public AddMetric(String name, Pair<String, String>... labels) {
        super(name, labels);
    }

    AddMetric(String name, Map<String, String> mapLabels) {
        super(name, mapLabels);
    }

    @Override
    public void doCollect(double value) {
        long l = counter.incrementAndGet();
        if (l > Long.MAX_VALUE - 10000) {
            reset();
        }
        adder.add(value);
    }

    public double getAvg() {
        long l = counter.get();
        if (l == 0) {
            return 0d;
        }
        return adder.sum() / l;
    }

    @Override
    public void reset() {
        this.adder.reset();
        counter.set(0);
    }

    @Override
    public double lastValue() {
        return adder.sum();
    }
}
