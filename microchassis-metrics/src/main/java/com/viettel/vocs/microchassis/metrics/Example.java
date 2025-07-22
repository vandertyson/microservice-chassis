/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.metrics;

import com.viettel.vocs.common.ocs.ApplicationID;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

/**
 * @author vttek
 */
class Example {

    public static void main(String[] args) throws InterruptedException, IOException {
        int numThread = 10;
        Metric metric = MetricCollector.registerApp(new ApplicationID("test", "test"));
        for (int i = 0; i < numThread; i++) {
            Executors.newSingleThreadExecutor().execute(() -> {
                while (true) {
                    metric.incr(MetricName.INFORMATION_ALL, SeriesType.avgT);
                    LockSupport.parkNanos(100);
                }
            });
        }
//        while (true) {
//            double lastValue = metric.getLastValue(MetricName.INFORMATION_ALL, SeriesType.avgT);
//            System.out.println(metric.mapMetrics.size());
//            System.out.println(lastValue);
//            List<String> result = CmdMetric.getCPU();
//            System.out.println("RESULT: \n" + result.stream().collect(Collectors.joining("\n")));
//            // Get current size of heap in bytes
//            long heapSize = Runtime.getRuntime().totalMemory();
//
//            // Get maximum size of heap in bytes. The heap cannot grow beyond this size.// Any attempt will result in an OutOfMemoryException.
//            long heapMaxSize = Runtime.getRuntime().maxMemory();
//
//            // Get amount of free memory within the heap in bytes. This size will increase // after garbage collection and decrease as new objects are created.
//            long heapFreeSize = Runtime.getRuntime().freeMemory();
//
//            System.out.println("heapSize " + byteToMb(heapSize) + " heapMaxSize " + byteToMb(heapMaxSize) + " heapFreeSize " + byteToMb(heapFreeSize));
//            System.out.println("===================================================================");
//            Thread.sleep(5000);
//        }
    }

    public static String byteToMb(long bytes) {
        return bytes / 1024 / 1024 + " mb";
    }
}
