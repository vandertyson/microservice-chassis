/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.metrics;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.ocs.ApplicationID;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.base.ChassisThreadManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.viettel.vocs.microchassis.base.ChassisConfig.PerformanceConfig.JVM_IMPORTER_CONFIG;

/**
 * @author vttek
 */
public class MetricCollector {

    // test
    public static void main(String[] args) {
        ApplicationID test = new ApplicationID("custhist", "test");
        MetricCollector.registerApp(test);
        while (true) {
            try {
                Thread.sleep(3000);
                Metric metric = mapMetrics.get(test);
                System.out.println(metric);
            } catch (Exception e) {

            }

        }
    }

    public static final long METRIC_CLEAR_CACHE_THRESHOLD_MS = TimeUnit.SECONDS.toMillis(ChassisConfig.MetricConfig.clearCacheThresholdSec.get());
    private static final Logger logger = LogManager.getLogger(MetricCollector.class);
    public static final Map<ApplicationID, Metric> mapMetrics = new ConcurrentHashMap<>();
    private static final List<ApplicationID> subscribedCPUMetrics = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static final Long start = System.currentTimeMillis();
    private static Scheduler scheduler;

    public static Set<ApplicationID> getSubscribedMetrics() {
        return mapMetrics.keySet();
    }

    public static final Runnable collectorThread = () -> {
        logger.info("Start CPU Monitor thread. pidstat={}, ps={}, intervalSec={}", ChassisConst.pidStatCommand, ChassisConst.topCommand, ChassisConfig.MetricConfig.metricCpuIntervalSec.getInt());
        List<String> lastCollectedThread = new ArrayList<>();
        while (ChassisThreadManager.enableMetricCollector.get() && TimeUtils.waitSafeSec(ChassisConfig.MetricConfig.metricCpuIntervalSec.getInt())) {
            try {
                List<PIDStatMetric> cpuInfo = getCPU();
                if (cpuInfo == null) break;
                if (logger.isDebugEnabled())
                    logger.debug("[Collect PIDStat metric]{size={}}", cpuInfo.size());
                //check de remove cac thread bat len xong bi tat di
                List<String> newThread = cpuInfo.stream().map(f -> f.threadName).collect(Collectors.toList());
                List<String> removed = lastCollectedThread.size() > 0
                        ? lastCollectedThread.stream().filter(f -> !newThread.contains(f)).collect(Collectors.toList())
                        : null;
                lastCollectedThread = newThread;
                TopMetric topInfo = getTop();

                getSubscribedMetrics().stream().map(mapMetrics::get).filter(Objects::nonNull)
                        .forEach(metric -> {
                            cpuInfo.stream().filter(pidStatMetric ->
                                            pidStatMetric.totalCpuConsume > 0
                                                    || !ChassisConfig.MetricConfig.METRIC_IGNORE_ZERO_CPU.get())
                                    .forEach(metric::setCpuMetric);
                            if (removed != null) removed.forEach(metric::removeCpuMetric);
                            if (topInfo != null) metric.setTopMetric(topInfo);
                        });
            } catch (Throwable ex) {
                logger.error(ex, ex);
            }
        }
    };
    public static final ApplicationID instanceAppId = CommonConfig.InstanceInfo.appID.get();
    public static Metric getMetric() {
        return getMetric(instanceAppId);
    }

    public static Metric getMetric(ApplicationID key) {
        return mapMetrics.get(key);
    }

    public static Metric registerApp() {
        return registerApp(instanceAppId);
    }

    public static Metric registerApp(ApplicationID key) {
        String collect = getAllMetricsKeys();
        if (mapMetrics.containsKey(key)) {
            logger.info("{} already registered to metric collector. MapMetrics size {}. Keys {}", key, mapMetrics.size(), collect);
            return mapMetrics.get(key);
        }
        Metric metric = new Metric(key);
        if (!"off".equals(JVM_IMPORTER_CONFIG.get())){
            metric = JvmMetricImporterFactory.createJvmMetricImporter().importJvmMetrics(metric);
        }
                if (ChassisThreadManager.threadCPU != null && !subscribedCPUMetrics.contains(key))
            subscribedCPUMetrics.add(key);
        mapMetrics.put(key, metric);
        logger.info("{} registered to metric collector. MapMetrics size {} Keys {}", key, mapMetrics.size(), collect);
        if (ChassisConfig.MetricConfig.METRIC_COLLECTOR_CONFIG.INTERVAL_DURATION.get() > 0
                && !started.compareAndExchange(false, true)
                && started.get()) run();
        return metric;
    }

    public void unregisterApp() {
        unregisterApp(CommonConfig.InstanceInfo.appID.get());
    }

    public void unregisterApp(ApplicationID key) {
        mapMetrics.remove(key);
        subscribedCPUMetrics.remove(key);
        logger.info("{} unegistered to metric collector. MapMetrics size {} Keys {}", key, mapMetrics.size(), getAllMetricsKeys());
    }

    private static String getAllMetricsKeys() {
        return mapMetrics.keySet().stream().map(ApplicationID::toString).collect(Collectors.joining(","));
    }

    public static boolean isRegistered() {
        return isRegistered(instanceAppId);
    }

    public static boolean isRegistered(ApplicationID key) {
        return mapMetrics.containsKey(key);
    }

    public static boolean isRunning() {
        return scheduler != null;
    }


    public static void run() {
        Date firstCollect = new Date(getCacheTime(ChassisConfig.MetricConfig.METRIC_COLLECTOR_CONFIG.getInterval(TimeUnit.MILLISECONDS)));
        String format = ChassisConfig.MetricConfig.METRIC_SIMPLE_DATE_FORMAT.format(firstCollect);
        logger.info("Metric collector started. intervalMs={}, cacheDurationMs={}, firstCollect=[{}]", ChassisConfig.MetricConfig.METRIC_COLLECTOR_CONFIG.getInterval(TimeUnit.MILLISECONDS), METRIC_CLEAR_CACHE_THRESHOLD_MS, format);
        try {
            scheduler = ChassisConfig.MetricConfig.METRIC_COLLECTOR_CONFIG.makeStdScheduler();
            scheduler.scheduleJob(JobBuilder.newJob(MetricCacheJob.class)
                    .withIdentity(new JobKey("cache", "metric-collector"))
                    .build(), TriggerBuilder.newTrigger()
                    .withIdentity("cache-trigger", "metric-collector")
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(ChassisConfig.MetricConfig.METRIC_COLLECTOR_CONFIG.getInterval(TimeUnit.MILLISECONDS))
                            .repeatForever())
                    .startAt(firstCollect)
                    .build());
            scheduler.start();
        } catch (Throwable ex) {
            logger.error(ex, ex);
        }
        started.set(true);
    }

    public static void stop() {
        logger.info("Stop metric collector. Clear cache");
        mapMetrics.clear();
        if (scheduler != null) {
            logger.info("Shutdown metric collector scheduler. Clear cache");
            try {
                scheduler.shutdown(true);
            } catch (Throwable ex) {
                logger.error(ex, ex);
            }
        }
    }

    private static TopMetric getTop() throws Exception {
        TopMetric result = null;
        Runtime r = Runtime.getRuntime();
        Process p = r.exec(ChassisConst.topCommand);
        String inputLine;
        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        while ((inputLine = in.readLine()) != null) {
            try {
                if (inputLine.contains("TIME")) continue;
                if (logger.isDebugEnabled()) logger.debug("[PS metric collected]{inputLine={}}", inputLine);
                result = TopMetric.parse(inputLine);
            } catch (Throwable ex) {
                logger.error(ex, ex);
                break;
            }
        }
        in.close();
        p.destroyForcibly();
        return result;
    }

    private static List<PIDStatMetric> getCPU() throws IOException {
        List<PIDStatMetric> result = new ArrayList<>();
        Runtime r = Runtime.getRuntime();
        Process p = null;
        try {
            p = r.exec(ChassisConst.pidStatCommand);
        } catch (Throwable ex) {
            logger.error(ex, ex);
            logger.error("Disable CPU statistic");
            ChassisThreadManager.enableMetricCollector.fuse();
            return null;
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String inputLine;
        int header = 0;
        while ((inputLine = in.readLine()) != null) {
            if (inputLine.contains("CPU")) header++;
            else if (inputLine.contains("java") || inputLine.contains("job-") || inputLine.isEmpty())
                continue;
            else {
                try {
                    PIDStatMetric parse = PIDStatMetric.parse(inputLine);
                    //duplicate thread name thi tang index len 1
                    long existed = result.stream().filter(f -> f.threadName.contains(parse.threadName)).count();
                    if (existed > 0) parse.threadName = parse.threadName + "_" + existed;
                    result.add(parse);
                } catch (Throwable ex) {
                    logger.error(ex, ex);
                }
            }
            if (header == 3) break;
        }
        in.close();
        p.destroyForcibly();
        return result;
    }


    public static void doCaching(long miliTimestamp) {
        if (logger.isDebugEnabled())
            logger.debug("Start caching metric. cacheTimestamp={}}", TimeUtils.miliToString(miliTimestamp));
        long startCalc = System.currentTimeMillis();
        try {
            mapMetrics.forEach((key, metric) -> {
                Calendar calendar = Calendar.getInstance();
                long lastCollectTime = metric.getLastCollectTime();
                float miliPassed = TimeUtils.nowMili() - lastCollectTime;
                if (METRIC_CLEAR_CACHE_THRESHOLD_MS > 0
                        && miliPassed > METRIC_CLEAR_CACHE_THRESHOLD_MS) {
                    calendar.setTimeInMillis(lastCollectTime);
                    metric.setLastCollectTime(miliTimestamp);
                    logger.info("Clear metrics {} after cached for {} ms. Last collect time {}. Data \n{}", key, miliPassed, ChassisConfig.MetricConfig.METRIC_SIMPLE_DATE_FORMAT.format(calendar.getTime()), metric.getMetricResponse());
                    metric.clearCache();
                } else metric.cache(miliTimestamp);
            });
            if (logger.isDebugEnabled())
                logger.debug("Finish caching metric. durationMs={}", TimeUtils.miliPassed(startCalc));
        } catch (Throwable ex) {
            logger.error(ex, ex);
        }
    }

    public static Long getCacheTime(long collectIntervalMs) {
        long now = TimeUtils.nowMili();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTime().getTime();
        long delta = now - startOfDay;
        long t = delta / collectIntervalMs;
//        long r = delta % collectIntervalMs;
        return startOfDay + t * collectIntervalMs;
//        if (r == 0) {
//            return startOfDay + t * collectIntervalMs;
//        } else {
//            return startOfDay + (t + 1) * collectIntervalMs;
//        }
    }

    public static boolean toggleCollect() {
        return PrometheusMetric.isSkip.toggle();
    }

    public static String report() {
        String collect = mapMetrics.entrySet().stream().map(f -> {
            String s = String.format("[lastCollect=%s, count=%d, cache=%d, log=%d]",
                    TimeUtils.miliToString(f.getValue().getLastCollectTime()),
                    f.getValue().mapMetrics.size(),
                    f.getValue().mapCacheMetrics.size(),
                    f.getValue().lastLogValue.size());
            return f.getKey().getPodType() + "=" + s;
        }).collect(Collectors.joining(","));
        return "Metric collector status. " +
                "collectIntervalMs=" + ChassisConfig.MetricConfig.METRIC_COLLECTOR_CONFIG.getInterval(TimeUnit.MILLISECONDS) +
                ", clearCacheThresholdMs=" + METRIC_CLEAR_CACHE_THRESHOLD_MS +
                ", mapMetrics=" + mapMetrics +
                ", threadCPU=" + ChassisThreadManager.threadCPU +
                ", subscribedCPUMetrics=" + subscribedCPUMetrics +
                ", pidStatCommand='" + ChassisConst.pidStatCommand + '\'' +
                ", topCommand='" + ChassisConst.topCommand + '\'' +
                ", cpuIntervalSecond=" + ChassisConfig.MetricConfig.metricCpuIntervalSec.getInt() +
                ", started=" + TimeUtils.miliToString(start) +
                ", maxCachePoint=" + ChassisConfig.MetricConfig.MAX_CACHE_POINT +
                ", info=" + collect +
                '}';
    }
}
