/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.metrics;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author vttek
 */
public class SetMetric extends PrometheusMetric {

    private final AtomicReference<Double> store = new AtomicReference<>(-1d);

    public SetMetric(String name, Pair<String, String>... labels) {
        super(name, labels);
    }

    SetMetric(String name, Map<String, String> mapLabels) {
        super(name, mapLabels);
    }

    @Override
    public void doCollect(double value) {
        store.set(value);
    }

    @Override
    public void reset() {
        store.set(-1d);
    }

    @Override
    public double lastValue() {
        return store.get();
    }
}
