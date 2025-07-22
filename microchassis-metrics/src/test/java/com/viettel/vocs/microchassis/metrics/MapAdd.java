/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.metrics;

import com.viettel.vocs.common.os.TimeUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author vttek
 */
class MapAdd {

    static Map<Integer, String> x = new ConcurrentHashMap<>();
    static AtomicLong count = new AtomicLong();

    @Test
    public void mapAdd() throws InterruptedException {
        int numThread = 20;
        for (int i = 0; i < numThread; i++) {
            Executors.newSingleThreadExecutor().execute(() -> {
                while (true) {
                    add();
                    count.incrementAndGet();
                }
            });
        }
        long last = count.get();
        for (int i = 0; i < 10; i++) {
            TimeUtils.waitSafeMili(1000);
            long get = count.get();
            long tps = (get - last) / 5;
            System.out.println(x.size() + " tps " + tps);
            last = get;
        }
    }

    static void add() {
        int name = 1;
        String get = x.get(name);
        if (get == null) {
            System.out.println("Add new");
            x.putIfAbsent(name, "1");
        }
    }
}
