/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.metrics;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.datatype.BooleanToggle;
import com.viettel.vocs.common.ocs.ApplicationID;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.os.thread.JobWithTimeout;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jctools.queues.MpmcArrayQueue;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author vttek
 */
public final class Metric {

    private static final Logger logger = LogManager.getLogger(Metric.class);
    public final Map<Integer, PrometheusMetric> mapMetrics = new ConcurrentHashMap<>();
    public final Queue<CachePoint> mapCacheMetrics = new MpmcArrayQueue<>(ChassisConfig.MetricConfig.MAX_CACHE_POINT.getInt());
    public final List<Pair<String, String>> sharedLabels = new ArrayList<>();
    private final ApplicationID id;
    private long lastCollectMilis = TimeUtils.nowMili();
    public static final BooleanToggle disableCollector = new BooleanToggle(false);
    private BooleanToggle pauseCollector = new BooleanToggle();

    public boolean isPause() {
        return pauseCollector.get() || disableCollector.get();
    }

    public Map<Integer, Double> lastLogValue = new ConcurrentHashMap<>();


    @Getter
    private final List<MetricEventHandler> eventHandlers = new CopyOnWriteArrayList<>();

    private enum EventType {
        METRIC_CACHE(eventHandlers -> new JobWithTimeout() {
            @Override
            public String getName() {
                return "metric cache success";
            }

            @Override
            public void run() {
                for (MetricEventHandler eventHandler : eventHandlers) {
                    try {

                        eventHandler.onMetricCache();

                    } catch (Throwable ex) {
                        logger.error(ex, ex);
                    }
                }
            }
        }),
        METRIC_FETCH(eventHandlers -> new JobWithTimeout() {
            @Override
            public String getName() {
                return "metric cache success";
            }

            @Override
            public void run() {
                for (MetricEventHandler eventHandler : eventHandlers) {
                    try {
                        synchronized (eventHandler) {
                            eventHandler.onMetricFetch();
                        }
                    } catch (Throwable ex) {
                        logger.error(ex, ex);
                    }
                }
            }
        });
        final Function<List<MetricEventHandler>, JobWithTimeout> triggerDef;

        public void run(List<MetricEventHandler> eventHandlers) {
            try {
                triggerDef.apply(eventHandlers).run();
            } catch (Throwable ex) {
                logger.error(ex, ex);
            }
        }

        EventType(Function<List<MetricEventHandler>, JobWithTimeout> triggerDef) {
            this.triggerDef = triggerDef;
        }
    }

    public static void main(String[] args) {
        MetricCollector.getMetric().addEventHandler(new MetricEventHandler() {
            @Override
            public void onMetricCache() {

            }

            @Override
            public void onMetricFetch() {

            }

            @Override
            public void onBeforeCache() {

            }
        });
        EventType.METRIC_FETCH.run(new ArrayList<>());
    }

    public Metric addEventHandler(MetricEventHandler eventHandler) {
        if (!eventHandlers.contains(eventHandler)) {
            eventHandlers.add(eventHandler);
        }
        return this;
    }

    public Metric removeEventHandler(MetricEventHandler eventHandler) {
        eventHandlers.remove(eventHandler);
        return this;
    }


    private static class CachePoint {
        @Getter
        public final long timestamp;
        @Getter
        public final List<CacheObject> points = new ArrayList<>();

        public CachePoint(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    @AllArgsConstructor
    private static class CacheObject {
        @Getter
        public final int hashCode;
        @Getter
        public final double data;
    }

    public Metric(ApplicationID id) {
        this.id = id;
        ;
        sharedLabels.add(Pair.of(ChassisConfig.TracingConfig.POD_TYPE_KEY.get(), Objects.requireNonNull(id.getPodType(), "ApplicationID " + id.toString() + " contains null podType, cannot create sharedLabel")));
        sharedLabels.add(Pair.of(ChassisConfig.TracingConfig.POD_ID_KEY.get(), Objects.requireNonNull(id.getPodId(), "ApplicationID " + id.toString() + " contains null podId, cannot create sharedLabel")));

        String vnf_instance_name = CommonConfig.InstanceInfo.VNF_INSTANCE_NAME.get();
        String podName = CommonConfig.InstanceInfo.POD_NAME.get();

        if (vnf_instance_name != null && !vnf_instance_name.isEmpty()) {
            //add mano instance name
            sharedLabels.add(Pair.of("vnf_instance_name", vnf_instance_name));
            logger.info("Added shared label. appId={}, vnf_instance_name={}", id, vnf_instance_name);
            //add mano pod ip
            sharedLabels.add(Pair.of(ChassisConfig.MetricConfig.INSTANCE_LABEL.get(), CommonConfig.InstanceInfo.MGR_IP + ":" + ChassisConfig.MetricConfig.METRIC_PORT));
            logger.info("Added shared label. appId={}, instance={}", id, CommonConfig.InstanceInfo.HOST_IP);
        }

        //add mano node name
        if (podName != null && !podName.isEmpty()) {
            sharedLabels.add(Pair.of(ChassisConfig.MetricConfig.NODE_NAME_LABEL.get(), podName));
            logger.info("Added shared label. appId={}, nodeName={}", id, CommonConfig.InstanceInfo.POD_NAME);
        }

        //initialize ocs metrics
        if (ChassisConfig.MetricConfig.DEFAULT_INIT_METRIC.get()) {
            Arrays.stream(MetricName.values()).filter(MetricName.INFORMATION_ALL::equals).forEach(mn -> {
                for (SeriesType st : SeriesType.values()) {
                    PrometheusMetric x;
                    switch (st.getType()) {
                        case ADD:
                            x = new AddMetric(mn.getLabel(), Pair.of(ChassisConfig.MetricConfig.SERIES_TYPE_KEY.get(), st.getLabelsName()));
                            mapMetrics.put(x.hashCode(), x);
                            break;
                        case SET:
                            x = new SetMetric(mn.getLabel(), Pair.of(ChassisConfig.MetricConfig.SERIES_TYPE_KEY.get(), st.getLabelsName()));
                            mapMetrics.put(x.hashCode(), x);
                            break;
                    }
                }
            });
        }
    }

    public synchronized long getLastCollectTime() {
        return lastCollectMilis;
    }

    public void setLastCollectTime() {
        setLastCollectTime(TimeUtils.nowMili());
    }

    public void setLastCollectTime(long lastCollectMs) {
        this.lastCollectMilis = lastCollectMs;
    }

    //region getLastValue
    public double getLastValue(PrometheusMetric key) {
        return getLastValue(key.hashCode());
    }

    private double getLastValue(int computeHash) {
        PrometheusMetric get;
        return ((get = mapMetrics.get(computeHash)) != null) ? get.lastValue() : 0;
    }

    public double getLastValue(MetricName name, String type) {
        return getLastValue(PrometheusMetric.computeHash(name.getLabel(), type));
    }

    public double getLastValue(String name, String type, Pair<String, String>... labels) {
        return getLastValue(PrometheusMetric.computeHash(name, type, labels));
    }

    public double getLastValue(String name, String type, List<Pair<String, String>> labels) {
        return getLastValue(PrometheusMetric.computeHash(name, type, labels));
    }

    public double getLastValue(String name, Pair<String, String>... labels) {
        return getLastValue(name, null, labels);
    }

    public double getLastValue(MetricName name, SeriesType type, Pair<String, String>... labels) {
        return getLastValue(name.getLabel(), type != null ? type.getLabelsName() : null, labels);
    }

    public double getLastValue(MetricName name, SeriesType type) {
        return getLastValue(name, type.getLabelsName());
    }

    public double getLastValue(MetricName name, Pair<String, String>... labels) {
        return getLastValue(name, null, labels);
    }

    //endregion
    //region incr
    public void incr(PrometheusMetric key) {
        add(key, 1.0);
    }

    public void incr(String name, String type, Pair<String, String>... labels) {
        add(name, type, 1.0, labels);
    }

    public void incr(String name, Pair<String, String>... labels) {
        add(name, 1.0, labels);
    }

    public void incr(MetricName name, SeriesType type, Pair<String, String>... labels) {
        incr(name.getLabel(), type.getLabelsName(), labels);
    }

    public void incr(MetricName name, String type, Pair<String, String>... labels) {
        incr(name.getLabel(), type, labels);
    }

    public void incr(String name, SeriesType type, Pair<String, String>... labels) {
        incr(name, type.getLabelsName(), labels);
    }

    //endregion
    private void recordMetric(PrometheusMetric key, double value, Function<PrometheusMetric, PrometheusMetric> newObjMaker) {
        if (disableCollector.get()) return;
        mapMetrics.computeIfAbsent(key.hashCode(), hash -> newObjMaker.apply(key)).collect(value);
    }

    public void recordMetric(String name, String type, double value, List<Pair<String, String>> labels, Class<? extends PrometheusMetric> clazz, Function<String, PrometheusMetric> newObjCreate) {
        if (disableCollector.get()) return;
        if (name == null || name.isEmpty()) {
            logger.error("Invalid metric format. Ignore {}. name={}, type={}, value={}", clazz.getSimpleName(), name, type, value);
            return;
        }

        int computeHash = PrometheusMetric.computeHash(name, type, labels);
        PrometheusMetric get = mapMetrics.get(computeHash);
        if (get == null) {
            get = newObjCreate.apply(name);
            if (type != null && !type.isEmpty()) {
                get.addLabels(Pair.of(ChassisConfig.MetricConfig.SERIES_TYPE_KEY.get(), type));
            }
            get.addLabels(labels);
            mapMetrics.putIfAbsent(computeHash, get);
        } else if (!(clazz.isInstance(get))) { //force metric type
            get = newObjCreate.apply(name);
            if (type != null && !type.isEmpty()) {
                get.addLabels(Pair.of(ChassisConfig.MetricConfig.SERIES_TYPE_KEY.get(), type));
            }
            get.addLabels(labels);
            mapMetrics.put(computeHash, get)
                    .getMapLabels().clear(); // equal with mapMetrics.remove(computeHash).getMapLabels().clear() before put new
        }
        mapMetrics.get(computeHash).collect(value);
    }
    //region add

    public void add(PrometheusMetric key, double value) {
        recordMetric(key, value, k -> new AddMetric(k.getName(), k.getMapLabels()));
    }

    public void add(String name, String type, double value, List<Pair<String, String>> labels) {
        recordMetric(name, type, value, labels, AddMetric.class, AddMetric::new);
    }

    public void add(MetricName name, SeriesType type, double value, Pair<String, String>... labels) {
        add(name.getLabel(), type.getLabelsName(), value, labels);
    }

    public void add(MetricName name, SeriesType type, double value, List<Pair<String, String>> labels) {
        add(name.getLabel(), type.getLabelsName(), value, labels);
    }

    public void add(MetricName name, String type, double value, List<Pair<String, String>> labels) {
        add(name.getLabel(), type, value, labels);
    }

    public void add(MetricName name, String type, double value, Pair<String, String>... labels) {
        add(name.getLabel(), type, value, labels);
    }

    public void add(String name, SeriesType type, double value, List<Pair<String, String>> labels) {
        add(name, type.getLabelsName(), value, labels);
    }

    public void add(String name, SeriesType type, double value, Pair<String, String>... labels) {
        add(name, type.getLabelsName(), value, labels);
    }

    public void add(String name, double value, List<Pair<String, String>> labels) {
        add(name, "", value, labels);
    }

    public void add(String name, double value, Pair<String, String>... labels) {
        add(name, "", value, labels);
    }

    public void add(String name, String type, double value, Pair<String, String>... labels) {
        add(name, type, value, Arrays.stream(labels).collect(Collectors.toList()));
    }

    //endregion
    //region set

    public void set(PrometheusMetric key, double value) {
        recordMetric(key, value, k -> new SetMetric(k.getName(), k.getMapLabels()));
    }


    public void set(String name, String type, double value, List<Pair<String, String>> labels) {
        recordMetric(name, type, value, labels, SetMetric.class, SetMetric::new);
    }

    public void set(String name, SeriesType type, double value, List<Pair<String, String>> labels) {
        set(name, type.getLabelsName(), value, labels);
    }

    public void set(String name, SeriesType type, double value, Pair<String, String>... labels) {
        set(name, type.getLabelsName(), value, labels);
    }

    public void set(String name, String type, double value, Pair<String, String>... labels) {
        set(name, type, value, Arrays.stream(labels).collect(Collectors.toList()));
    }

    public void set(MetricName name, SeriesType type, double value, List<Pair<String, String>> labels) {
        set(name.getLabel(), type.getLabelsName(), value, labels);
    }

    public void set(MetricName name, SeriesType type, double value, Pair<String, String>... labels) {
        set(name.getLabel(), type.getLabelsName(), value, labels);
    }

    public void set(MetricName name, String type, double value, List<Pair<String, String>> labels) {
        set(name.getLabel(), type, value, labels);
    }

    public void set(MetricName name, String type, double value, Pair<String, String>... labels) {
        set(name.getLabel(), type, value, labels);
    }

    public void set(MetricName name, double value, List<Pair<String, String>> labels) {
        set(name.getLabel(), "", value, labels);
    }

    public void set(MetricName name, double value, Pair<String, String>... labels) {
        set(name.getLabel(), "", value, labels);
    }

    public void set(String name, double value, Pair<String, String>... labels) {
        set(name, "", value, labels);
    }

    public void set(String name, double value, List<Pair<String, String>> labels) {
        set(name, "", value, labels);
    }

    //endregion
    //region observe
    public void observe(PrometheusMetric key, double value) {
        recordMetric(key, value, k -> new ObserveMetric(k.getName(), k.getMapLabels()));
    }

    public void observe(String name, String type, double value, List<Pair<String, String>> labels) {
        recordMetric(name, type, value, labels, ObserveMetric.class, ObserveMetric::new);
    }

    public void observe(String name, String type, double value, Pair<String, String>... labels) {
        observe(name, type, value, Arrays.stream(labels).collect(Collectors.toList()));
    }

    public void observe(String name, SeriesType type, double value, Pair<String, String>... labels) {
        observe(name, type.getLabelsName(), value, labels);
    }

    public void observe(MetricName name, SeriesType type, double value, Pair<String, String>... labels) {
        observe(name.getLabel(), type.getLabelsName(), value, labels);
    }

    public void observe(MetricName name, String type, double value, Pair<String, String>... labels) {
        observe(name.getLabel(), type, value, labels);
    }

    public void observe(MetricName name, double value, Pair<String, String>... labels) {
        observe(name.getLabel(), "", value, labels);
    }

    public void observe(String name, double value, Pair<String, String>... labels) {
        observe(name, "", value, labels);
    }

    //endregion
    public void setCpuMetric(PIDStatMetric cpuMetric) {
        Pair<String, String> threadName = Pair.of("threadName", cpuMetric.threadName);
        try {
            if (!"-".equals(cpuMetric.cpuID))
                set("CPU", "cpuID", Double.parseDouble(cpuMetric.cpuID), threadName);
            set("CPU", "totalCpuConsume", cpuMetric.totalCpuConsume, threadName);
            set("CPU", "usrCpuConsume", Double.parseDouble(cpuMetric.usrCpuConsume), threadName);
            set("CPU", "sysCpuConsume", Double.parseDouble(cpuMetric.sysCpuConsume), threadName);
        } catch (Throwable ex) {
            logger.error(ex, ex);
        }
    }

    public void removeCpuMetric(String threadName) {
        Pair<String, String> labels = Pair.of("threadName", threadName);
        try {
            remove("CPU", "cpuID", labels);
            remove("CPU", "totalCpuConsume", labels);
            remove("CPU", "usrCpuConsume", labels);
            remove("CPU", "sysCpuConsume", labels);
        } catch (Throwable ex) {
            logger.error(ex, ex);
        }
    }

    public void remove(String name, String type, Pair<String, String>... labels) {
        int hash = PrometheusMetric.computeHash(name, type, labels);
        PrometheusMetric remove = mapMetrics.remove(hash);
        if (logger.isInfoEnabled() && remove != null) {
            logger.info("Metric is removed. name={}, type={}, hash={}", name, type, hash);
        }
    }

    public void remove(String name, Pair<String, String>... labels) {
        int hash = PrometheusMetric.computeHash(name, null, labels);
        PrometheusMetric remove = mapMetrics.remove(hash);
        if (logger.isInfoEnabled() && remove != null) {
            logger.info("Metric is removed. name={}, hash={}", name, hash);
        }
    }

    public void reset(String name, String type, Pair<String, String>... labels) {
        PrometheusMetric prometheusMetric = mapMetrics.get(PrometheusMetric.computeHash(name, type, labels));
        if (prometheusMetric != null) prometheusMetric.reset();
    }

    public void reset(MetricName name, String type, Pair<String, String>... labels) {
        reset(name.getLabel(), type, labels);
    }

    public void reset(MetricName name, SeriesType type, Pair<String, String>... labels) {
        reset(name.getLabel(), type.getLabelsName(), labels);
    }

    public void reset(String name, SeriesType type, Pair<String, String>... labels) {
        reset(name, type.getLabelsName(), labels);
    }

    public void reset(String name, Pair<String, String>... labels) {
        reset(name, "", labels);
    }

    public void cache(long time) {
        long start = TimeUtils.nowNano();
        eventHandlers.forEach(eventHandler -> { // safe to iterrate without check empty
            try {
                eventHandler.onBeforeCache();
            } catch (Throwable ex) {
                logger.error(ex, ex);
            }
        });
        if (!eventHandlers.isEmpty() && logger.isDebugEnabled())
            logger.debug("[Before caching eventHandler]{appId={}, timeMs={}}", id, TimeUtils.miliPassed(start));
        if (isPause()) {
            if (logger.isDebugEnabled())
                logger.debug("[Collector pause is enable. Drop cache point]{app={}, timestamp={}, time={}}", id, time, TimeUtils.miliToString(time));
            return;
        }
        long key = (time > 0) ? time : System.currentTimeMillis();
        CachePoint cachePoint = new CachePoint(time);
        List<CacheObject> caches = cachePoint.getPoints();
        Map<String, List<Pair<String, Double>>> toLog = new HashMap<>();
        for (PrometheusMetric value : mapMetrics.values()) {
            int hash = value.hashCode();
            CacheObject t = new CacheObject(hash, value.lastValue());
            if (toBeLog(value)) {
                double logValue;
                if (value instanceof AddMetric) {
                    logValue = t.getData() - lastLogValue.computeIfAbsent(hash, f -> 0d);
                    lastLogValue.put(hash, t.getData());
                } else {
                    logValue = t.getData();
                }
                toLog.computeIfAbsent(value.getName(), k -> new ArrayList<>())
                        .add(Pair.of(value.getMapLabels().get(ChassisConfig.MetricConfig.SERIES_TYPE_KEY.get()), logValue));
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Metric cached. hash={}, name={}, labels={}",
                        hash, value.getName(), value.getMapLabels());
            }
            caches.add(t);
        }
        if (!toLog.isEmpty()) logCache(toLog, key);
        boolean offer;
        CachePoint poll;
        long startEnqueue = TimeUtils.nowNano();
        if (ChassisConfig.MetricConfig.CACHE_METRIC_MAX_ENQUEUE_MS.get() <= 0) {
            if (!(offer = mapCacheMetrics.offer(cachePoint))) {
                if ((poll = mapCacheMetrics.poll()) != null)
                    logger.error("Failed to cache metric after. Drop last entry. time={}, points={}, dropData={}",
                            time, caches.size(), cachePointToString(poll));
                offer = mapCacheMetrics.offer(cachePoint);
            }
        } else {
            while (!(offer = mapCacheMetrics.offer(cachePoint)) && TimeUtils.waitSafeNano(ChassisConfig.MetricConfig.CACHE_METRIC_PARK_NANO.get())) { // khong offer duoc thi wait, duoc thi break
                if (TimeUtils.miliPassed(startEnqueue) > ChassisConfig.MetricConfig.CACHE_METRIC_MAX_ENQUEUE_MS.get()) {
                    if ((poll = mapCacheMetrics.poll()) != null)
                        logger.error("Failed to cache metric after {} ms. Drop last entry. points={}, drop={}",
                                ChassisConfig.MetricConfig.CACHE_METRIC_MAX_ENQUEUE_MS, cachePointToString(poll), caches.size());
                    offer = mapCacheMetrics.offer(cachePoint);
                    break;
                }
            }
        }
        if (offer) {
            if (logger.isDebugEnabled())
                logger.debug("Value cached. time={},  mapCacheSize={}, mapMetricsSize={}, countPair={}, duration={}",
                        TimeUtils.miliToString(time), mapCacheMetrics.size(), mapMetrics.size(), caches.size(), TimeUtils.miliPassed(startEnqueue));
            triggerEvent(EventType.METRIC_CACHE);
        }
    }

    private String cachePointToString(CachePoint point) {
        return TimeUtils.miliToString(point.getTimestamp()) + "_" + point.getPoints().size();
    }


    private void logCache(Map<String, List<Pair<String, Double>>> toLog, Long time) {
        StringBuilder stringBuilder = new StringBuilder();
        AtomicInteger count = new AtomicInteger();
        toLog.forEach((key, value) -> {
            stringBuilder.append(key).append(" ").append(ChassisConfig.MetricConfig.METRIC_SIMPLE_DATE_FORMAT.format(time)).append(" ");
            value.sort(Map.Entry.comparingByKey());
            int j = 0;
            for (Pair<String, Double> p : value) {
                stringBuilder.append(p.getKey()).append("=").append(p.getValue());
                if (j < value.size() - 1) stringBuilder.append("|");
                j++;
            }
            if (count.getAndIncrement() < toLog.size() - 1) stringBuilder.append("\n");
        });
        logger.info("\n{}", stringBuilder);
    }

    private boolean toBeLog(PrometheusMetric value) {
        return ChassisConfig.MetricConfig.METRIC_LOG_CACHE.get()
                && MetricName.INFORMATION_ALL.getLabel().equals(value.getName())
                && value.getMapLabels().containsKey(ChassisConfig.MetricConfig.SERIES_TYPE_KEY.get());
    }

    public String getMetricResponse() {
        StringBuilder sb = new StringBuilder();
        if (!mapCacheMetrics.isEmpty() && !isPause()) {
            synchronized (mapCacheMetrics) {
                int count = 0;
                while (!mapCacheMetrics.isEmpty() && count <= ChassisConfig.MetricConfig.MAX_FETCH_POINT.get()) {
                    CachePoint entry = mapCacheMetrics.poll();
                    if (entry != null) {
                        count++;
                        Long timeStamp = entry.getTimestamp();
                        for (CacheObject p : entry.getPoints()) {
                            Integer hash = p.getHashCode();
                            Double value = p.getData();
                            PrometheusMetric metric = mapMetrics.get(hash);
                            String labels = metric.getLabelPrometheusString(sharedLabels);
                            sb.append(metric.getName());
                            if (labels != null && !labels.isEmpty()) sb.append("{").append(labels).append("} ");
                            sb.append(value).append(" ").append(timeStamp).append("\n");
                        }
                    }
                }
                if (logger.isDebugEnabled())
                    logger.debug("[Finish fetch getMetricResponse]{appId={}, count={}}", id, count);
            }
        } else {
            for (PrometheusMetric value : mapMetrics.values()) {
                String labels = value.getLabelPrometheusString(sharedLabels);
                sb.append(value.getName());
                if (labels != null && !labels.isEmpty())
                    sb.append("{").append(labels).append("} ");
                sb.append(value.lastValue()).append(" ").append("\n");
            }
        }
        setLastCollectTime();
        triggerEvent(EventType.METRIC_FETCH);
        return sb.toString();
    }

    private void triggerEvent(EventType type) {
        if (!eventHandlers.isEmpty()) {
            try {
                CompletableFuture.runAsync(() -> type.run(eventHandlers), ThreadManager.sharedThreadPoolExecutor).get();
            } catch (Throwable ex) {
                logger.error(ex, ex);
            }
        }
    }

    public void reset() {
        mapMetrics.values().forEach(PrometheusMetric::reset);
    }

    public synchronized void clearCache() {
        setLastCollectTime();
        logger.info("Metric cache is cleared. Log all to output\n{}", getMetricResponse());
        mapCacheMetrics.clear();
    }

    public synchronized void clear() {
        clearCache();
        mapMetrics.clear();
    }

    public void setTopMetric(TopMetric topInfo) {
        try {
            set("CPU", "topCpu", Double.parseDouble(topInfo.cpu));
            set("CPU", "topResource", topInfo.resource);
            set("CPU", "topUpMinute", topInfo.upTime);
        } catch (Throwable ex) {
            logger.error(ex, ex);
        }
    }

    public List<Pair<PrometheusMetric, List<Pair<Long, Double>>>> getAllCachedData() {
        Map<Integer, List<Pair<Long, Double>>> mapData = new HashMap<>();
        int count = 0;
//		while (!mapCacheMetrics.isEmpty() && count <= ChassisConfig.MetricConfig.MAX_FETCH_POINT.get()) {
//			CachePoint poll = mapCacheMetrics.poll();
//			if (poll != null) {
//				count++;
//				Long time = poll.getTimestamp();
//				mapData = poll.getPoints().stream()
//					.collect(Collectors.groupingBy(CacheObject::getHashCode, Collectors.mapping(pair -> Pair.of(time, pair.getData()), Collectors.toList())));
//			}
//		}
        while (!mapCacheMetrics.isEmpty() && count <= ChassisConfig.MetricConfig.MAX_FETCH_POINT.get()) {
            CachePoint poll = mapCacheMetrics.poll();
            if (poll != null) {
                count++;
                List<CacheObject> value = poll.getPoints();
                Long time = poll.getTimestamp();
                for (CacheObject pair : value) {
                    if (!mapData.containsKey(pair.getHashCode())) {
                        mapData.put(pair.getHashCode(), new ArrayList<>());
                    }
                    //check if metadata is no longer available
                    if (!mapMetrics.containsKey(pair.getHashCode())) {
                        continue;
                    }
                    mapData.get(pair.getHashCode()).add(Pair.of(time, pair.getData()));
                }
            }
        }
        if (logger.isDebugEnabled()) logger.debug("[Finish fetch getAllCachedData]{appId={}, count={}}", id, count);
        List<Pair<PrometheusMetric, List<Pair<Long, Double>>>> ret = mapData.entrySet().stream()
                .map(entry -> {
                    PrometheusMetric key = mapMetrics.get(entry.getKey());
                    return Pair.of(key, entry.getValue());
                })
                .filter(f -> {
                    boolean leftEmpty = f.getLeft() == null;
                    return !leftEmpty;
                })
                .collect(Collectors.toList());
        if (ret.isEmpty()) {
            logger.warn("getAllCachedData for metric return empty");
        }
        setLastCollectTime();
        triggerEvent(EventType.METRIC_FETCH);
        return ret;
    }

    public boolean togglePauseCollector() {
        return pauseCollector.toggle();
    }
}
