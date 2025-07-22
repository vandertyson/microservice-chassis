package com.viettel.vocs.microchassis.metrics;

import com.viettel.vocs.common.CommonConst;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.ocs.ApplicationID;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MetricTest {
    protected final Logger logger = LogManager.getLogger(this.getClass());

    @BeforeAll
    public void setUpClass() {
        LogUtils.setupLog4j2();
    }

    @Test
    public void test() throws InterruptedException {
        int numThread = 10;
        int numRun = 2000000;
        Metric metric = MetricCollector.registerApp(new ApplicationID("test", "test"));
        AtomicLong cacheCount = new AtomicLong();
        AtomicLong cacheFetch = new AtomicLong();
        metric.addEventHandler(new MetricEventHandler() {

            @Override
            public void onMetricCache() {
                long l = cacheCount.incrementAndGet();
                if (l % 10 == 0) {
                    logger.info("On metric cached");
                }
            }

            @Override
            public void onMetricFetch() {
                cacheFetch.incrementAndGet();
                logger.info("On metric fetch");
            }

            @Override
            public void onBeforeCache() {

            }
        });
        ExecutorService es = Executors.newFixedThreadPool(numThread);
        for (int i = 0; i < numThread; i++) {
            es.execute(() -> {
                long t = System.currentTimeMillis();
                for (int i1 = 0; i1 < numRun; i1++) {
                    metric.set(MetricName.INFORMATION_ALL, SeriesType.avgT, 14.0);
                    metric.add(MetricName.INFORMATION_ALL, SeriesType.requests, 2.0);
                    metric.incr(MetricName.INFORMATION_ALL, "custom series");
                    metric.incr(MetricName.INFORMATION_ALL, SeriesType.under4ms);
                    metric.observe("observe", 5.0);
                }
                List<Pair<PrometheusMetric, List<Pair<Long, Double>>>> allCachedData = metric.getAllCachedData();
                System.out.println(Thread.currentThread() + " finish " + (System.currentTimeMillis() - t) + " ms");
            });
        }
        es.shutdown();
        es.awaitTermination(10000, TimeUnit.SECONDS);
        assertEquals(1.0 * numThread * numRun, metric.getLastValue(MetricName.INFORMATION_ALL, SeriesType.under4ms));
        assertEquals(2.0 * numThread * numRun, metric.getLastValue(MetricName.INFORMATION_ALL, SeriesType.requests));
        assertEquals(1.0 * numThread * numRun, metric.getLastValue(MetricName.INFORMATION_ALL, "custom series"));
        assertEquals(14.0, metric.getLastValue(MetricName.INFORMATION_ALL, SeriesType.avgT));
        double observe = metric.getLastValue("observe");
        logger.info("metric.getLastValue('observe') = {}", observe);
        assertTrue(Math.abs(observe - 5.0) < 1e-4);
        assertTrue(cacheCount.get() > 0);
        TimeUtils.waitSafeMili(1000);
        assertEquals(numThread, cacheFetch.get());
//        MetricName[] names = MetricName.values();
//        SeriesType[] types = SeriesType.values();
//        assertEquals(metric.mapMetrics.size(), names.length + 1);
    }

    @Test
    public void doubleAdderTEst() throws InterruptedException {
        int numThread = 3;
        int numRun = 1000;
        DoubleAdder dd = new DoubleAdder();
        ExecutorService es = Executors.newSingleThreadExecutor();
        for (int i = 0; i < numThread; i++) {
            es.execute(() -> {
                for (int i1 = 0; i1 < numRun; i1++) {
                    dd.add(1.0);
                }
            });
        }
        es.shutdown();
        es.awaitTermination(10000, TimeUnit.SECONDS);
        double lastValue = dd.doubleValue();
        assertEquals(1.0 * numThread * numRun, lastValue);
    }

    @Test
    public void doubleAdderResetTest() throws InterruptedException {
        int numThread = 3;
        int numRun = 1000;
        DoubleAdder dd = new DoubleAdder();
        ExecutorService es = Executors.newSingleThreadExecutor();
        for (int i = 0; i < numThread; i++) {
            es.execute(() -> {
                for (int i1 = 0; i1 < numRun; i1++) {
                    dd.reset();
                    dd.add(6699);
                }
            });
        }
        es.shutdown();
        es.awaitTermination(10000, TimeUnit.SECONDS);
        double lastValue = dd.doubleValue();
        assertEquals(6699, lastValue);
    }

    @Test
    public void hashTest() {
        Pair<String, String> podType = Pair.of("pod_type", "etst");
        Pair<String, String> podID = Pair.of("pod_id", "etst");
        Set<Integer> setHash = new HashSet<>();
        MetricName[] names = MetricName.values();
        SeriesType[] types = SeriesType.values();
        for (MetricName mn : names) {
            for (SeriesType st : types) {
                int computeHash = PrometheusMetric.computeHash(mn.getLabel(), st.toString(), podType, podID);
                setHash.add(computeHash);
            }
        }
        assertEquals(setHash.size(), names.length * types.length);
    }

    @Test
    public void roundTime() {
        try {
            int collectInterval = 5000;
            for (int i = 0; i < 10; i++) {
                Long cacheTime = MetricCollector.getCacheTime(collectInterval);
                SimpleDateFormat sdf = new SimpleDateFormat(CommonConst.DATE_TIME_FORMAT);
                System.out.println(sdf.format(cacheTime));
                TimeUtils.waitSafeMili(collectInterval);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void delayStart() {
        System.setProperty("chassis.metric.collectIntervalSec", "2");
        MetricCollector.registerApp(new ApplicationID("test", "test"));
        long l = System.currentTimeMillis();
        while (!MetricCollector.isRunning()) {
            if (System.currentTimeMillis() - l > 10_000) {
                fail("Time out start caching thread");
                return;
            }
            LockSupport.parkNanos(10000);
        }
        assertEquals(MetricCollector.isRunning(), true);
    }

    @Test
    public void testGetAllCached() throws InterruptedException {
        Metric metric = MetricCollector.registerApp(new ApplicationID("test", "test"));
        int maxCollect = 100;
        int testTimeMs = 60_000;
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                int count = 0;
                while (true) {
                    try {
                        Thread.sleep(1000);
                        metric.incr("counter");
                        count++;
                        if (count >= maxCollect) {
                            logger.info("Finish collect");
                            break;
                        }
                    } catch (Exception ex) {
                        logger.error(ex, ex);
                    }
                }
            }
        });
        long l = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() - l > testTimeMs) {
                break;
            }
            logger.info("Running...");
            Thread.sleep(5000);
        }
        List<Pair<PrometheusMetric, List<Pair<Long, Double>>>> allCachedData = metric.getAllCachedData();
        long expectedPointCount = testTimeMs / ChassisConfig.MetricConfig.METRIC_COLLECTOR_CONFIG.getInterval(TimeUnit.MILLISECONDS);
        logger.info("Point per metric {}. Expected {}", allCachedData.get(0).getValue().size(), expectedPointCount);
        assertTrue(allCachedData.size() > 1);
        assertTrue(allCachedData.get(0).getValue().size() >= 1);
    }
}
