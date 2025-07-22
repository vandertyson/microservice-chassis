/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.mano.service;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.LRUMapAsCache;
import com.viettel.vocs.common.datatype.CollectionUtils;
import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.common.ocs.ApplicationID;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.os.thread.JobWithTimeout;
import com.viettel.vocs.common.os.thread.SharedThreadPoolManager;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.mano.model.*;
import com.viettel.vocs.mano.reload.APIReloadManager;
import com.viettel.vocs.mano.reload.ReloadAPI;
import com.viettel.vocs.mano.reload.ReloadAPIParam;
import com.viettel.vocs.mano.service.ManoService.ManoServiceType;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpAPI;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpMessageHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.client.ClientConnection;
import com.viettel.vocs.microchassis.connection.client.NettyClient;
import com.viettel.vocs.microchassis.connection.event.ClientEvent;
import com.viettel.vocs.microchassis.connection.event.ConnectionEvent;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.exception.appender.Appender;
import com.viettel.vocs.microchassis.exception.client.ExceptionMonitor;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes;
import com.viettel.vocs.microchassis.http.client.HttpInstanceClient;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import com.viettel.vocs.microchassis.metrics.Metric;
import com.viettel.vocs.microchassis.metrics.MetricCollector;
import com.viettel.vocs.microchassis.metrics.PrometheusMetric;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jctools.queues.MpmcArrayQueue;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.viettel.vocs.microchassis.connection.event.ClientEvent.EventType.*;
import static com.viettel.vocs.microchassis.connection.event.ConnectionEvent.EventType.*;

/**
 * @author vttek
 */
public class ManoIntegration {
    static {
        ChassisConfig.MetricConfig.METRIC_PUSH_CONFIG.INTERVAL_DURATION.setDefault(30000L);
    }

    public static final Appender LOG_ONLY_APPENDER = new Appender() {
        @Override
        public boolean sendMessage(String messageID, byte[] data, String endPoint) {
            ExceptionMonitor.logger.error("No appender found. Log only. messageId={}, endpoint={}", messageID, endPoint);
            return false;
        }

        @Override
        public void close() {
            ExceptionMonitor.logger.error("LOG_ONLY_APPENDER close");
        }
    };
    private static final Logger logger = LogManager.getLogger(ManoIntegration.class);
    private static final AtomicBoolean useHash = new AtomicBoolean(ChassisConfig.MetricConfig.METRIC_PUSH_HASH_ENABLE.get());
    @Getter
    private static boolean enable = ChassisConfig.ManoConfig.MANO_ENABLE.get();
    public static boolean isAlarm = ChassisConfig.ManoConfig.AlarmConfig.INIT_ALARM_STATE.get();

    static synchronized boolean toggleHashMetric() {
        boolean firstSaw = useHash.get();
        if (firstSaw) useHash.compareAndExchange(true, false);
        else useHash.compareAndExchange(false, true);
        return firstSaw;
    }

    static void clear(Handler<String> handler, String type) {
        if (type.equalsIgnoreCase("metric")) {
            int size = queueMetric.size();
            queueMetric.clear();
            MetricPushThread.countRetry.clear();
            handler.handle("Clear " + size + " cached data");
            return;
        }
        if (type.equalsIgnoreCase("alarm")) {
            int size = mapAlarmSuccess.size();
            mapAlarmSuccess.clear();
            mapMessageID.clear();
            handler.handle("Clear " + size + " cached mapAlarmSuccess");
            return;
        }
        if (type.equalsIgnoreCase("callback")) {
            int size = mapManoCallBack.size();
            mapManoCallBack.clear();
            handler.handle("Clear " + size + " cached mapManoCallBack");
            return;
        }
        if (type.equalsIgnoreCase("helper")) {
            String cleared = ManoAlarmHelper.clearWatching();
            handler.handle(cleared);
            return;
        }
        handler.handle("Invalid cache name. Available ['metric', 'alarm', 'callback', 'helper']");
    }

    static void reportThreadPool(HttpServerContext hctx) {
        Map<String, List<String>> params = hctx.getInParams();
        List<String> action = params.get("action");
        String threadPoolStatusReport = SharedThreadPoolManager.report();
        if (action != null && !action.isEmpty()) {
            String act = action.get(0);
            if (act.equalsIgnoreCase("clear")) {
                SharedThreadPoolManager.clearMapProgress();
                hctx.send(threadPoolStatusReport);
            } else if (act.equalsIgnoreCase("purge")) {
                ThreadManager.sharedThreadPoolExecutor.purgeJobQueue();
                hctx.send(threadPoolStatusReport);
            } else if (act.equalsIgnoreCase("empty")) {
                ThreadManager.sharedThreadPoolExecutor.clearJobQueue();
                hctx.send(threadPoolStatusReport);
            } else hctx.send("Invalid action. Available actions ['clear','purge', 'empty']");
        } else {
            hctx.send(threadPoolStatusReport);
        }
    }

    static String toggleAlarmHandle(Map<String, List<String>> params) {
        List<String> key = params.get("key");
        String resultMsg;
        if (key != null && !key.isEmpty()) {
            synchronized (Alarm.ignoredAlarmCodeKeywords) {
                String s = key.get(0);
                if (Alarm.ignoredAlarmCodeKeywords.contains(s)) {
                    Alarm.ignoredAlarmCodeKeywords.remove(s);
                    resultMsg = "Mano alarm is enabled for keyword " + s;
                } else {
                    Alarm.ignoredAlarmCodeKeywords.add(s);
                    resultMsg = "Mano alarm is disabled for keyword " + s;
                }
            }
        } else {
            boolean isAlarmed = toggleAlarm();
            resultMsg = isAlarmed ? "Mano alarm is enabled" : "Mano alarm is disabled";
        }
        return resultMsg;
    }

    static void clearHandle(HttpServerContext hctx) {
        Map<String, List<String>> params = hctx.getInParams();
        List<String> key = params.get("target");
        if (key == null || key.isEmpty()) hctx.send(HttpResponseStatus.SERVICE_UNAVAILABLE);
        else {
            Handler<String> handler = HttpAPI.makeOnResultStringCallback(hctx);
            String type = key.get(0);
            if (type.equalsIgnoreCase("metric")) {
                int size = queueMetric.size();
                queueMetric.clear();
                MetricPushThread.countRetry.clear();
                handler.handle(String.format("Clear %d cached data", size));
            } else if (type.equalsIgnoreCase("alarm")) {
                int size = mapAlarmSuccess.size();
                mapAlarmSuccess.clear();
                mapMessageID.clear();
                handler.handle(String.format("Clear %d cached mapAlarmSuccess", size));
            } else if (type.equalsIgnoreCase("callback")) {
                int size = mapManoCallBack.size();
                mapManoCallBack.clear();
                handler.handle(String.format("Clear %d cached mapManoCallBack", size));
            } else handler.handle("Invalid cache name. Available ['metric', 'alarm', 'callback']");
        }
    }

    public static void startTrackingExceptions() {
        if (ExceptionMonitor.logger.isDebugEnabled())
            ExceptionMonitor.logger.debug("start ExceptionTracking");
//        ChassisConfig.ExceptionClientConfig.ClientConfig.getConfigFromEnv();
//        ChassisConfig.ExceptionClientConfig.RestConfig.getConfigFromEnv();

        for (int i = 0; i < ChassisConfig.ExceptionClientConfig.ClientConfig.SENDER_POOL_SIZE.get(); i++) {
            ExceptionMonitor.PushException pushException = new ExceptionMonitor.PushException(customAppender);
            ExceptionMonitor.mapTheadSender.put(String.valueOf(i), pushException);
            pushException.start();
        }
    }

    public static boolean isAlarm() {
        return enable && isAlarm;
    }

    static class ManoHttp1ClientHandler extends HttpClientHandler {
        ManoHttp1ClientHandler(HttpClientOptions httpClientOptions) {
            super(httpClientOptions);
        }

        @Override
        public void handle(HttpClientReceiveContext hctx) {
            logger.info("[MANO receive async response]{ctx={}, msg={}}", hctx.getChannelString(), hctx.getInMsg());
        }
    }

    private static final Object lock = new Object();
    public static final String ALARM_TYPE = "AlarmNotification";
    private static volatile ManoIntegration instance;

    static {
        ManoAlarmHelper.start();
    }

    protected static final Map<String, Pair<String, HttpMessageHandler<HttpResponse>>> mapManoCallBack = Collections.synchronizedMap(new LRUMapAsCache(ChassisConfig.ManoConfig.AlarmConfig.MAP_WAIT_ALARM_SIZE.getInt()));
    private static int manoNotifyResultCode = -1;

    private static final ConcurrentLinkedDeque<Pair<String, List<MetricPushBody>>> queueMetric = new ConcurrentLinkedDeque<>();
    private static final Map<ManoServiceType, ManoService> mapManoService = new EnumMap<>(ManoServiceType.class);
    private static final Queue<JobWithTimeout> queue = new MpmcArrayQueue<>(ChassisConfig.ManoConfig.ManoPool_CONFIG.QUEUE_SIZE.getInt());
    protected static final Map<String, String> mapAlarmSuccess = Collections.synchronizedMap(new LRUMapAsCache(ChassisConfig.ManoConfig.AlarmConfig.MAP_WAIT_ALARM_SIZE.getInt()));
    private static final AtomicBoolean pauseMetricPush = new AtomicBoolean();
    protected static final Map<String, CompletableFuture<Boolean>> mapMessageID = new ConcurrentHashMap<>();
    private static AccessTokenResponse token;
    private static final AtomicInteger doPrintMetric = new AtomicInteger();
    private static Runnable metricJob;
    private static ScheduledFuture<?> metricPush;
    private static List<MetricPushThread> metricPushThread;

    public static int isDoPrintMetric() {
        return doPrintMetric.get();
    }

    public static void setDoPrintMetric(int n) {
        doPrintMetric.set(n);
    }

    public static boolean toggleAlarm() {
        isAlarm = !isAlarm;
        return isAlarm;
    }

    public static void authen(Handler<String> handler) {
        SharedThreadPoolManager.executeAsync(new JobWithTimeout() {
            @Override
            public String getName() {
                return "Mano authen with callback at " + TimeUtils.miliToString(System.currentTimeMillis());
            }

            @Override
            public void run() {
                doAuthen(handler);
            }
        });
    }

    public static void notification(Handler<String> handler) {
        SharedThreadPoolManager.executeAsync(new JobWithTimeout() {
            @Override
            public String getName() {
                return "Mano notification with callback at " + TimeUtils.miliToString(System.currentTimeMillis());
            }

            @Override
            public void run() {
                doNotify(handler);
            }
        });
    }

    public static boolean isMetricPushEnable() {
        return enable && ChassisConfig.MetricConfig.METRIC_PUSH_CONFIG.INTERVAL_DURATION.get() > 0;
    }

    public static String getMapDetail() {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("alarm_not_resolved", mapAlarmSuccess.keySet());
            result.put("alarm_message_id", mapMessageID.keySet());
            result.put("metric_retry_key", MetricPushThread.countRetry.keySet());
            result.put("map_alarm_callback", mapManoCallBack.keySet());
            return JsonUtils.getEncoder().toJson(result);
        } catch (Throwable ex) {
            return ex.getMessage();
        }
    }


    public static void onInstance(Consumer<ManoIntegration> ifEnableManoAction, Runnable ifNotEnableManoAction) {
        if (enable && ifEnableManoAction != null) ifEnableManoAction.accept(getInstance());
        else if (ifNotEnableManoAction != null) ifNotEnableManoAction.run();
    }

    public static void onInstance(Consumer<ManoIntegration> ifEnableManoAction) {
        onInstance(ifEnableManoAction, null);
    }

    public static ManoIntegration getInstance() {
        ManoIntegration r = instance;
        if (r == null) {
            synchronized (lock) {
                r = instance;
                if (r == null && enable) instance = new ManoIntegration();
            }
        }
        return r;
    }

    @Getter
    private static HttpInstanceClient manoHttpClient;
    //	private static void registerService(ManoServiceType type, String url){
//		try {
//			ManoService manoService = new ManoService(new URL(url));
//			mapManoService.put(type, manoService);
//		} catch (Throwable e) {
//			logger.error(e, e);
//		}
//	}
//	private static void loadConfig() {
//		registerService(ManoServiceType.ALARM, ChassisConfig.ManoConfig.MANO_RECEIVED_ALARM_URI.get());
//		registerService(ManoServiceType.AUTHEN, ChassisConfig.ManoConfig.MANO_AUTHORIZATION_SERVER.get());
//		registerService(ManoServiceType.NOTIFICATION, ChassisConfig.ManoConfig.VNFC_NOTIFICATION_ENDPOINT.get());
//		registerService(ManoServiceType.METRIC, ChassisConfig.ManoConfig.MANO_METRIC_RECEIVE_URI.get());
//		mapManoService.forEach((serviceType, manoService)->logger.info("MANO {} service {}", serviceType, manoService));
//	}
    static Appender customAppender = LOG_ONLY_APPENDER;

    public ManoIntegration() {
        try {
            HttpClientOptions httpClientConfig = ConnectionManager.getInstance().getHttpClientConfig(ChassisConst.ManoConst.manoHttpClientId);
            if (httpClientConfig == null) {
                logger.info("Not found mano client config from ConnectionManager, use default");
                httpClientConfig = new HttpClientOptions(ChassisConst.ManoConst.manoHttpClientId); // default if not set
            }
            manoHttpClient = new HttpInstanceClient(httpClientConfig, new ManoHttp1ClientHandler(httpClientConfig));
            ManoAlarmHelper.start();
            SharedThreadPoolManager.executeAsync(new JobWithTimeout() {
                @Override
                public String getName() {
                    return "Mano authen";
                }

                @Override
                public void run() {
                    doAuthen(null);
                }
            });

            customAppender = new Appender() {
                @Override
                public boolean sendMessage(String messageId, byte[] data, String endPoint) {
                    try {
                        JobWithTimeout job = new JobWithTimeout() {
                            @Override
                            public String getName() {
                                return "Mano_alarm_job_" + messageId;
                            }

                            @Override
                            public void run() {
                                Pair<String, HttpMessageHandler<HttpResponse>> remove = mapManoCallBack.remove(messageId);
                                if (remove == null) {
                                    logger.error("Invalid action for messageId {}", messageId);
                                    return;
                                }
                                String type = remove.getKey();
                                if ("alarm".equals(type)) {
                                    mapMessageID.put(messageId, new CompletableFuture<>());
                                }
                                HttpMessageHandler<HttpResponse> clientHandler = remove.getValue();
                                try {
                                    HttpResponse res = sendToMano(ManoServiceType.ALARM, data, false, null, false, 1);
                                    if (res != null) {
                                        logger.info("[Receive mano {} response]{messageId={}, status={}, headers={}, content={}}", type, messageId, res.status(), res.headers(), res.toString(StandardCharsets.UTF_8));
                                        if (clientHandler != null) {
                                            SharedThreadPoolManager.executeAsync(new JobWithTimeout() {
                                                @Override
                                                public String getName() {
                                                    return "Mano alarm call back " + messageId;
                                                }

                                                @Override
                                                public void run() {
                                                    try {
                                                        clientHandler.handle(res);
                                                    } catch (Throwable ex) {
                                                        logger.error(ex, ex);
                                                    } finally {
                                                        res.decompose();
                                                    }
                                                }
                                            });
                                        } else {
                                            res.decompose();
                                        }
                                    } else {
                                        if (clientHandler != null) {
                                            SharedThreadPoolManager.executeAsync(new JobWithTimeout() {
                                                @Override
                                                public String getName() {
                                                    return "Mano alarm call back " + messageId;
                                                }

                                                @Override
                                                public void run() {
                                                    try {
                                                        clientHandler.timeoutHandle(messageId);
                                                    } catch (Throwable ex) {
                                                        logger.error(ex, ex);
                                                    }
                                                }
                                            });
                                        }
                                        logger.info("[Can not receive mano {} response]{messageId={}", type, messageId);
                                    }
                                } catch (Throwable ex) {
                                    logger.error(ex, ex);
                                }
                                if ("alarm".equals(type)) {
                                    CompletableFuture<Boolean> remove1 = mapMessageID.remove(messageId);
                                    if (remove1 != null) {
                                        remove1.complete(true);
                                    }
                                }
                            }
                        };
                        if (!isAuthenticated() || !isNotified()) {
                            addJobToQueue(job);
                        } else {
                            job.run();
                        }
                    } catch (Throwable ex) {
                        logger.error(ex, ex);
                    }
                    return true;
                }

                @Override
                public void close() {

                }
            };
            ExceptionMonitor.customDropHandler.add(entry -> {
                logger.error("Exception monitor drop message. key={} value={}", entry.getKey(), entry.getValue());
                String messageId = mapAlarmSuccess.remove(entry.getKey());
                if (messageId != null) {
                    mapManoCallBack.remove(messageId);
                    CompletableFuture<Boolean> wait = mapMessageID.remove(messageId);
                    if (wait != null) {
                        try {
                            wait.complete(true);
                        } catch (Throwable ex) {
                            logger.error(ex, ex);
                        }
                    }
                }
            });
            startTrackingExceptions();
            if (isMetricPushEnable()) {
                initMetricPusher();
            } else {
                logger.error("MANO metric url is not configured. Metric wil not be pushed");
            }
            ThreadManager.JobMonitor.scheduleJob(new ThreadManager.JobMonitor.Job() {
                @Override
                public String getId() {
                    return "log mano queue";
                }

                @Override
                public Runnable getJob() {
                    return () -> logger.info("Mano status. metricPushIntervalSec={}, " + "metricQueue={}, mapAlarmSuccess={}, " + "mapAlarmId={}, mapCallback={}, jobQueue={}," + "retryMetricSize={}", ChassisConfig.MetricConfig.METRIC_PUSH_CONFIG.INTERVAL_DURATION, queueMetric.size(), mapAlarmSuccess.size(), mapMessageID.size(), mapManoCallBack.size(), queue.size(), MetricPushThread.countRetry.size());
                }

                @Override
                public int getInitialDelay() {
                    return 5;
                }

                @Override
                public int getInterval() {
                    return 5;
                }

                @Override
                public TimeUnit getTimeUnit() {
                    return TimeUnit.SECONDS;
                }
            });
        } catch (Throwable e) {
            logger.error(e, e);
        }
    }

    public static boolean toggleMetricPush() {
        boolean b = pauseMetricPush.get();
        return pauseMetricPush.compareAndExchange(!b, b);
    }

    private static void initMetricPusher() {
        if (!enable) {
            logger.error("mano.enable is {}. Will not push metric", enable);
            return;
        }
        if (!isMetricPushEnable()) {
            logger.error("metricPushIntervalSec is {}. Will not push metric", ChassisConfig.MetricConfig.METRIC_PUSH_CONFIG.INTERVAL_UNIT.toSeconds(ChassisConfig.MetricConfig.METRIC_PUSH_CONFIG.INTERVAL_DURATION.read()));
            return;
        }

        MetricPusher pusher = job -> {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            try {
                String s = JsonUtils.getEncoder().toJson(job.getValue());

                byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
                long startPush = System.currentTimeMillis();
                HttpResponse res = sendToMano(ManoServiceType.METRIC, bytes, false, null, false, ChassisConfig.MetricConfig.MANO_METRIC_PUSH_RETRY.getInt());
                if (res != null) {
                    logger.info("Mano push metric response. payloadSize={}, status={}, headers={}, content={}, latencyMs={}, key={}", bytes.length, res.status(), res.headers(), res.content() != null ? res.toString(StandardCharsets.UTF_8) : "", System.currentTimeMillis() - startPush, job.getKey());
                    if (ChassisConfig.ManoConfig.MANO_SUCCESS_CODES.contains(res.status().code())) {
                        result.complete(true);
                    } else {
                        result.complete(false);
                    }
                    res.decompose();
                } else {
                    logger.info("Failed to push metric to mano. key={}}", job.getKey());
                    result.complete(false);
                }
            } catch (Throwable e) {
                logger.error(e, e);
                logger.info("Exception when push metric to mano. key={}", job.getKey());
                result.complete(false);
            }
            return result;
        };

        reloadLogger.info("Register simulateThresholdAlarm function");
        registerAlarmSimulation();

        metricJob = new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isAuthenticated()) doAuthen(null);
                    if (logger.isDebugEnabled()) logger.debug("[Mano fetch cached metrics and push]");
                    for (ApplicationID appID : MetricCollector.getSubscribedMetrics()) {
                        long startFetch = System.currentTimeMillis();
                        String now = TimeUtils.nowToString(ChassisConfig.MetricConfig.METRIC_KEY_PATTERN.get());
                        String jobKeyPrefix = appID.getPodId() + "_" + appID.getPodType() + "_" + now + "_batch_";
                        Metric metric = MetricCollector.getMetric(appID);
                        List<Pair<PrometheusMetric, List<Pair<Long, Double>>>> allCachedData = metric.getAllCachedData();
                        if (allCachedData == null || allCachedData.isEmpty()) {
                            logger.info("[Empty metric. Someone has fetched the cache earlier (-_-)]{appID={}}", appID);
                            continue;
                        }
                        long endFetch = System.currentTimeMillis();
                        if (pauseMetricPush.get()) {
                            if (logger.isDebugEnabled())
                                logger.debug("[Metric push is paused. Metric is fetched from cache but not pushed]{appId={}, fetched={}, timeMs={}}", appID, allCachedData.size(), endFetch - startFetch);
                            continue;
                        }
                        long startSplit = System.currentTimeMillis();
                        List<MetricPushBody> body = allCachedData.stream().map(datum -> {
                                    MetricPushBody mpd = new MetricPushBody();
                                    try {
                                        PrometheusMetric first = datum.getLeft();
                                        mpd.setMetricName(first.getName());
                                        first.getMapLabels().forEach((key, value) -> mpd.getMetadata().put(key, value));
                                        metric.sharedLabels.forEach(sharedLabel -> mpd.getMetadata().put(sharedLabel.getKey(), sharedLabel.getValue()));
                                        mpd.setObjectInstanceId(CommonConfig.InstanceInfo.VNF_INSTANCE_ID.get());
                                        mpd.setSubObjectInstanceId(CommonConfig.InstanceInfo.VNFC_ID.get());

                                        if (MetricMock.isValid()) {
                                            String seriesType = first.getMapLabels().get(ChassisConfig.MetricConfig.SERIES_TYPE_KEY.get());
                                            datum.getValue().forEach(longDoublePair ->
                                                    mpd.valueAtTimestamp(MetricMock.getNextMetricValue(first.getName(), seriesType, new HashMap<>(first.getMapLabels()), longDoublePair.getValue(), reloadLogger), longDoublePair.getKey())
                                            );
                                        } else {
                                            datum.getValue().forEach(longDoublePair -> mpd.valueAtTimestamp(longDoublePair.getValue(), longDoublePair.getKey()));
                                        }
                                        return mpd;
                                    } catch (Exception ex) {
                                        logger.error("error processing. metric={}, job={}", mpd.getMetricName(), jobKeyPrefix);
                                        logger.error(ex, ex);
                                    }
                                    return mpd;
                                }).filter(f -> !StringUtils.isEmpty(f.getMetricName()))
                                .collect(Collectors.toList());
                        Map<String, List<MetricPushBody>> pushJobs = splitJob(jobKeyPrefix, body);
                        long endSplit = System.currentTimeMillis();
                        logger.info("[Finish metric fetch]{podType={}, numBody={}, countPush={}, fetchMs={}, splitMs={}}", appID.getPodType(), body.size(), pushJobs.size(), endFetch - startFetch, endSplit - startSplit);
                        pushJobs.forEach((key, value) -> {
                            if (queueMetric.size() > ChassisConfig.MetricConfig.QUEUE_METRIC_SIZE.get()) {
                                logger.error("Drop metric push. Map cache metric is full. key={}, size={}", key, value.size());
                                MetricPushThread.countRetry.remove(key);
                            } else {
                                queueMetric.addFirst(Pair.of(key, value));
                            }
                        });
                        final int printMetric = doPrintMetric.get();
                        if (printMetric > 0) {
                            logger.info("Schedule write metric to file {}", jobKeyPrefix);
                            try {
                                CompletableFuture.runAsync(() -> {
                                    pushJobs.forEach((key, value) -> {
                                        String tail = printMetric == 1 ? ".txt" : ".json";
                                        String fileName = key + tail;
                                        if (printMetric == 1) {
                                            try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), StandardCharsets.UTF_8))) {
                                                for (MetricPushBody bd : value)
                                                    writer.write(bd.format() + "\n");

                                                logger.info("Success write metric to txt file. key={}", fileName);
                                            } catch (Throwable e) {
                                                logger.info("Error write metric to file. key={}, mode={}", fileName, printMetric);
                                                logger.error(e, e);
                                            }
                                        } else {
                                            try (Writer writer = new FileWriter(fileName)) {
                                                JsonUtils.getEncoder().toJson(value, writer);
                                                logger.info("Success write metric to json file. key={}", fileName);
                                            } catch (Throwable ex) {
                                                logger.info("Error write metric to file. key={}, mode={}", fileName, printMetric);
                                                logger.error(ex, ex);
                                            }
                                        }
                                    });
                                }, ThreadManager.sharedThreadPoolExecutor);

                            } catch (Throwable e) {
                                logger.error(e, e);
                            } finally {
                                doPrintMetric.set(0);
                            }
                        }
                    }
                } catch (Throwable ex) {
                    logger.error(ex, ex);
                }
            }


            private Map<String, List<MetricPushBody>> splitJob(String jobKeyPrefix, List<MetricPushBody> body) {
                Map<String, List<MetricPushBody>> ret = new HashMap<>();
                List<MetricPushBody> alls = new ArrayList<>();
                for (MetricPushBody mpd : body) {
                    if (mpd.getValues().size() > ChassisConfig.MetricConfig.MAX_DATUM_PER_OBJECT.get()) {
                        List<List<Double>> chunkedValue = CollectionUtils.chunked(mpd.getValues(), ChassisConfig.MetricConfig.MAX_DATUM_PER_OBJECT.getInt());
                        List<List<Long>> chunkedTime = CollectionUtils.chunked(mpd.getTimestamps(), ChassisConfig.MetricConfig.MAX_DATUM_PER_OBJECT.getInt());
                        for (int i = 0; i < chunkedValue.size(); i++) {
                            MetricPushBody cmpd = mpd.cloneMetaData();
                            cmpd.setValues(chunkedValue.get(i));
                            cmpd.setTimestamps(chunkedTime.get(i));
                            alls.add(cmpd);
                        }
                    } else {
                        alls.add(mpd);
                    }
                }
                if (alls.size() > ChassisConfig.MetricConfig.MAX_OBJECT_PER_PUSH.get()) {
                    List<List<MetricPushBody>> chunked = CollectionUtils.chunked(body, ChassisConfig.MetricConfig.MAX_OBJECT_PER_PUSH.getInt());
                    for (int i = 0; i < chunked.size(); i++) {
                        List<MetricPushBody> pushData = chunked.get(i);
                        String key = jobKeyPrefix + (i + 1) + "_of_" + chunked.size();
                        ret.put(key, pushData);
                    }
                } else {
                    String key = jobKeyPrefix + "1_of_1";
                    ret.put(key, body);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("[Split metric push]{maxDatumPerObject={}, maxObjectPerPush={}, org={}, countPush={}, push={}}", ChassisConfig.MetricConfig.MAX_DATUM_PER_OBJECT, ChassisConfig.MetricConfig.MAX_OBJECT_PER_PUSH, body.size(), ret.size(), ret.entrySet().stream().map(f -> {
                        IntSummaryStatistics collect = f.getValue().stream().map(p -> p.getValues().size()).collect(Collectors.summarizingInt(Integer::intValue));
                        return f.getKey() + ":" + collect.getMin() + "|" + collect.getMax() + "|" + collect.getAverage();
                    }).collect(Collectors.joining(",")));
                }
                return ret;
            }
        };

        try {
            metricPush = ThreadManager.scheduleWithFixedDelay(MetricPushThread.metricPushPool, metricJob, ChassisConfig.MetricConfig.METRIC_PUSH_CONFIG);
            logger.info("Metric fetch thread scheduled");
        } catch (Throwable e) {
            logger.error(e, e);
        }
        metricPushThread = new ArrayList<>();
        for (int i = 0; i < ChassisConfig.MetricConfig.numPusherThread.get(); i++) {
            MetricPushThread thread = new MetricPushThread(queueMetric, pusher, ChassisConfig.ManoConfig.MANO_REQUEST_TIMEOUT_SEC.getInt(), ChassisConfig.MetricConfig.QUEUE_METRIC_SIZE.getInt());
            metricPushThread.add(thread);
            thread.start();
        }
        logger.info("Metric push thread scheduled");
    }

    public static Triple<String, Double, Long> formatMetricLine(String line) {
        String name;
        double value;
        long time;
        int i = line.indexOf("}");
        if (i < 0) {
            String[] s = line.split(" ");
            if (s.length < 2) {
                return null;
            }
            name = s[0].trim();
            try {
                value = Double.parseDouble(s[1]);
            } catch (Throwable ex) {
                logger.error(ex, ex);
                return null;
            }
            if (s.length == 2) {
                time = System.currentTimeMillis();
            } else {
                time = Long.parseLong(s[2]);
            }
        } else {
            name = line.substring(0, i + 1).trim();
            String vals = line.substring(i + 1).trim();
            String[] s = vals.split(" ");
            if (s.length == 0) {
                return null;
            }
            try {
                value = Double.parseDouble(s[0]);
            } catch (Throwable ex) {
                logger.error(ex, ex);
                return null;
            }
            if (s.length == 1) {
                time = System.currentTimeMillis();
            } else {
                time = Long.parseLong(s[1]);
            }
        }
        return new ImmutableTriple<>(name, value, time);
    }

    private static void doAuthen(Handler<String> callback) {
        try {
            HttpResponse res = sendToMano(ManoServiceType.AUTHEN, JsonUtils.getEncoder().toJson(new GrantType()).getBytes(StandardCharsets.UTF_8), true, null, true, 5);
            if (res == null) {
                if (callback != null) {
                    callback.handle(null);
                }
                throw new Exception("No response for authen");
            }

            handleTokenResponse(callback, res);
        } catch (Throwable ex) {
            logger.error(ex, ex);
        }
    }

    private static void handleTokenResponse(Handler<String> callback, HttpResponse res) {
        String bodyRes = res.toStringUTF8();
        try {
            token = JsonUtils.getDecoder().fromJson(bodyRes, AccessTokenResponse.class);
            logger.info("Access Token response {}", JsonUtils.getEncoder().toJson(token));
            if (callback != null) {
                callback.handle(token.access_token);
            }
            doQueueJob();
        } catch (Throwable e) {
            logger.error(e, e);
            logger.error("Mano authen response {}", bodyRes);
            if (callback != null) {
                callback.handle(null);
            }
        } finally {
            res.decompose();
        }
    }

    private static void doQueueJob() {
        while (queue.size() > 0) {
            JobWithTimeout poll = queue.poll();
            if (poll != null) {
                logger.info("[MANO do queue job]{jobName={}, remain={}}", poll.getName(), queue.size());
                SharedThreadPoolManager.executeAsync(poll);
            }
        }
        logger.info("[MANO queue job empty]");
    }

    public static void notification() {
        if (ChassisConfig.ManoConfig.MANO_USE_ASYNC_REQUEST.get()) {
            JobWithTimeout notifyJob = new JobWithTimeout() {
                @Override
                public String getName() {
                    return "Mano notify";
                }

                @Override
                public void run() {
                    doNotify(null);
                }
            };
            if (!isAuthenticated()) {
                addJobToQueue(notifyJob);
                return;
            }
            SharedThreadPoolManager.executeAsync(notifyJob);
        } else {
            doAuthen(arg0 -> doNotify(null));
        }
    }

    private static void doNotify(Handler<String> callback) {
        try {
            ManoPayload manoPayload = new ManoPayload(CommonConfig.InstanceInfo.VNF_INSTANCE_ID.get(), CommonConfig.InstanceInfo.VNFC_ID.get());
            String payload = JsonUtils.getEncoder().toJson(manoPayload);
            logger.info("Send mano notification payload {}", payload);
            HttpResponse res = sendToMano(ManoServiceType.NOTIFICATION, payload.getBytes(StandardCharsets.UTF_8), false, null, true, ChassisConfig.ManoConfig.MANO_RETRY_COUNT.getInt());
            if (res == null) {
                logger.error("No response for MANO notification");
                manoNotifyResultCode = 0;
                ApplicationCheckList.getInstance().initialCheckJob(false, CheckType.MONITOR, new ObjectOrExceptionChecker() {
                    @Override
                    public String process() {
                        return "Timeout or !=" + ChassisConfig.ManoConfig.MANO_SUCCESS_CODES + " for MANO notification";
                    }

                    @Override
                    public String getCheckListID() {
                        return "MANO_NOTIFICATION";
                    }
                });
                doQueueJob();
                if (callback != null) {
                    callback.handle(null);
                }
                return;
            }
            String body = String.format("Receive mano notification response. status=%s, headers=%s, content=%s}", res.status(), res.headers(), res.toString(StandardCharsets.UTF_8));
            logger.info(body);
            ApplicationCheckList.getInstance().initialCheckJob(false, CheckType.MONITOR, new ObjectOrExceptionChecker<>() {
                @Override
                public String process() {
                    return body;
                }

                @Override
                public String getCheckListID() {
                    return "MANO_NOTIFICATION";
                }
            });
            manoNotifyResultCode = res.status().code();
            doQueueJob();
            res.decompose();
            if (callback != null) callback.handle(body);
        } catch (Throwable e) {
            logger.error(e, e);
            if (callback != null) callback.handle(null);
        }
    }

    private static boolean isAuthenticated() {
        return token != null;
    }

    public static boolean isNotified() {
        return token != null && manoNotifyResultCode > -1;
    }

    public static void addJobToQueue(JobWithTimeout jobWithTimeout) {
        try {
            long l = TimeUtils.nowNano();
            boolean offer;
            if (ChassisConfig.ManoConfig.MANO_MAX_ENQUEUE_MS.get() > 0) {
                do {
                    if (TimeUtils.isTimeoutMili(l, ChassisConfig.ManoConfig.MANO_MAX_ENQUEUE_MS.get())) {
                        logger.info("[Mano full queue. Drop job]{job={}, size={}}", jobWithTimeout.getName(), queue.size());
                        return;
                    }
                    offer = queue.offer(jobWithTimeout);
                } while (!offer && TimeUtils.waitSafeNano(10000));
                logger.info("[Mano is not authenticated. Add job to queue]{job={}, size={}}", jobWithTimeout.getName(), queue.size());
            } else {
                offer = queue.offer(jobWithTimeout);
                if (!offer) {
                    logger.info("[Mano full queue. Drop job]{job={}, size={}}", jobWithTimeout.getName(), queue.size());
                }
            }
        } catch (Throwable ex) {
            logger.error(ex, ex);
        }
    }

    public static HttpResponse sendToMano(HttpMethod method, String url, byte[] payload, boolean basicAuth, Consumer<HttpRequest> reqAlter, long timeoutMs) {
        return sendToMano(ManoServiceType.API.getHost(), ManoServiceType.API.getPort(), method, url, payload, basicAuth, reqAlter, timeoutMs, false, 1);
    }

    private static HttpResponse sendToMano(ManoServiceType serviceType, byte[] payload, boolean basicAuth, Consumer<HttpRequest> reqAlter, boolean verifyResponse, int maxRetry) {
        return sendToMano(serviceType.getHost(), serviceType.getPort(), HttpMethod.POST, serviceType.getPath(), payload, basicAuth, reqAlter, manoHttpClient.getConfig().sendTimeoutMs, verifyResponse, maxRetry);
    }

    private static HttpResponse sendToMano(String host, int port, HttpMethod method, String url, byte[] payload, boolean basicAuth, Consumer<HttpRequest> reqAlter, long timeout, boolean verifyResponse, int maxRetry) {
        /**
         * send neu verifyResponse thi check trong list status code (vd 200,204) neu khong thoa man thi retry, default 20 lan
         * 	giua moi lan retry wait default 5s
         */
        if (!enable)
            return null; // MANO_ENABLE=false -> no send to mano, auto receive null response just like timeout
        HttpResponse res = null;
        HttpRequest rq = null;
        try {
            if (token == null && !basicAuth) doAuthen(null);

            int count = 0;
            int send = maxRetry + 1;

            for (int i = 0; i < send; i++) {
                count++;
                try {
                    rq = payload != null ? manoHttpClient.createReq(method, url, payload) : manoHttpClient.createReq(method, url);
                    if (reqAlter != null) reqAlter.accept(rq);
                    rq.headers().set(HttpHeaderNames.AUTHORIZATION, basicAuth ? ("Basic " + ChassisConfig.ManoConfig.bearerToken.get()) : (token.token_type + " " + token.access_token));
                    rq.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
                    rq.headers().set("ClientID", CommonConfig.InstanceInfo.VNFC_ID.get());
                    res = manoHttpClient.sendSync(host, port, rq, timeout); // for both H1 and H2 response
                    if (res == null) {
                        logger.info("Receive mano null response, wait {}s and retry {}", ChassisConfig.ManoConfig.MANO_WAIT_RETRY, count);
                        if (ChassisConfig.ManoConfig.MANO_WAIT_RETRY.get() > 0)
                            TimeUtils.waitSafeMili(TimeUnit.SECONDS.toMillis(ChassisConfig.ManoConfig.MANO_WAIT_RETRY.get()));
                        continue;
                    }
                    if (verifyResponse && !ChassisConfig.ManoConfig.MANO_SUCCESS_CODES.contains(res.status().code())) {
                        String manoContent = res.toStringUTF8();
                        logger.info("[Can not receive mano response. Wait {}s and retry {}]{errorCode={}, msg={}}", ChassisConfig.ManoConfig.MANO_WAIT_RETRY, count, res.status().code(), manoContent);
                        res.decompose();
                        if (ChassisConfig.ManoConfig.MANO_WAIT_RETRY.get() > 0) {
                            TimeUtils.waitSafeMili(TimeUnit.SECONDS.toMillis(ChassisConfig.ManoConfig.MANO_WAIT_RETRY.get()));
                        }
                        continue;
                    }
                    return res;
                } catch (Throwable e) {
                    logger.error(e);
                    logger.info("Error send mano Url {}. Wait {} seconds and retry {}", url, ChassisConfig.ManoConfig.MANO_WAIT_RETRY, count);
                    if (ChassisConfig.ManoConfig.MANO_WAIT_RETRY.get() > 0) {
                        TimeUtils.waitSafeMili(TimeUnit.SECONDS.toMillis(ChassisConfig.ManoConfig.MANO_WAIT_RETRY.get()));
                    }
                }
            }
            logger.error("Can not get a success response from {} after {} retry", url, count);
            if (res != null) res.decompose();
            return null;
        } finally {
            if (rq != null) rq.decompose();
        }
    }


    public static void close() {
        ExceptionMonitor.stop();
        if (metricPush != null) {
            metricPush.cancel(false);
        }
        if (metricJob != null) {
            try {
                System.out.println("Mano last push start");
                metricJob.run();
            } catch (Exception ex) {
                logger.error(ex, ex);
            }
        }
        if (metricPushThread != null) {
            for (MetricPushThread pushThread : metricPushThread) {
                pushThread.stopGracefully();
            }
        }
        TimeUtils.waitSafeSec(ChassisConfig.ManoConfig.MANO_REQUEST_TIMEOUT_SEC.getInt());
        System.out.println("Mano close");
    }

    static Consumer<ClientEvent> clientEventHandler = param -> {
        ClientEvent.EventType sourceEvent = param.getType();
        String resolveCode = ManoAlarmHelper.eventToResolveKey(param);
        logger.info("[Mano handle service event]{event={}, resolveCode={}, service={}}", sourceEvent, resolveCode, param.getServiceName());
        switch (sourceEvent) {
            case CONNECT_SUCCESS:
                new Alarm(ChassisConst.Name.INTERNAL_CONNECTION_SERVICE_DOWN.name(), resolveCode).resolve();
                break;
            case DISCOVERY_SUCCESS:
                new Alarm(ChassisConst.Name.INTERNAL_SERVICE_DISCOVERY_FAIL.name(), resolveCode).resolve();
                break;
            case CONNECT_FAIL:
                new CustomAlarm(ChassisConst.Name.INTERNAL_CONNECTION_SERVICE_DOWN.name(), resolveCode, param.message, AlarmTypes.PerceivedSeverityType.CRITICAL).alarm();
                break;
            case DISCOVERY_FAIL:
                new CustomAlarm(ChassisConst.Name.INTERNAL_SERVICE_DISCOVERY_FAIL.name(), resolveCode, param.message, AlarmTypes.PerceivedSeverityType.CRITICAL).alarm();
                break;
            default:
                break;
        }
    };

    static Consumer<ConnectionEvent> connectionEventHandler = param -> {
        ClientConnection clientConnection = param.getConnection();
        ConnectionEvent.EventType sourceEvent = param.getType();
        String resolveCode = ManoAlarmHelper.eventToResolveKey(param);
        logger.info("[MANO handler connection event triggered]{event={}, clientId={}, baseService={}, host={}, port={}}", sourceEvent, clientConnection.getClientId(), clientConnection.getBaseServiceName(), clientConnection.getHostName(), clientConnection.getPort());
        switch (sourceEvent) {
            case CONNECTION_UP:
                new Alarm(ChassisConst.Name.INTERNAL_CONNECTION_DISCONNECT.name(), resolveCode).resolve();
                break;
            case CONNECTION_INIT_SUCCESS:
                new Alarm(ChassisConst.Name.INTERNAL_CONNECTION_CREATED_FAIL.name(), resolveCode).resolve();
                break;
            case CONNECTION_WRITE_SUCCESS:
                new Alarm(ChassisConst.Name.INTERNAL_CONNECTION_EXCEPTION.name(), resolveCode).resolve();
                break;
            case SERVICE_NAME_VERIFICATION_SUCCESS:
                new Alarm(ChassisConst.Name.INTERNAL_SERVICE_NAME_VERIFICATION_FAIL.name(), resolveCode).resolve();
                break;
            case CONNECTION_DOWN:
                new CustomAlarm(ChassisConst.Name.INTERNAL_CONNECTION_DISCONNECT.name(), resolveCode, param.message, AlarmTypes.PerceivedSeverityType.MAJOR).alarm();
                break;
            case CONNECTION_WRITE_FAIL:
                new CustomAlarm(ChassisConst.Name.INTERNAL_CONNECTION_EXCEPTION.name(), resolveCode, param.message, AlarmTypes.PerceivedSeverityType.MINOR).alarm();
                break;
            case CONNECTION_INIT_FAIL:
                new CustomAlarm(ChassisConst.Name.INTERNAL_CONNECTION_CREATED_FAIL.name(), resolveCode, param.message, AlarmTypes.PerceivedSeverityType.MINOR).alarm();
                break;
            case TIMEOUT_ISOLATION:
                new CustomAlarm(ChassisConst.Name.ISOLATION_CHANNEL_OVER_TIMEOUT_REQUEST.name(), resolveCode, param.message, AlarmTypes.PerceivedSeverityType.MINOR).alarm();
                break;
            case SERVICE_NAME_VERIFICATION_FAIL:
                new CustomAlarm(ChassisConst.Name.INTERNAL_SERVICE_NAME_VERIFICATION_FAIL.name(), resolveCode, param.message, AlarmTypes.PerceivedSeverityType.MINOR).alarm();
                break;
        }
    };

    public static void addAutoAlarmServer(NettyServer... servers) {
        if (!enable) {
            logger.warn("mano.enable is not {}. Ignore addAutoAlarmServer", enable);
            return;
        }
        if (servers == null || servers.length == 0) {
            return;
        }
        for (NettyServer server : servers) {
            //TODO: add server event mano alarm
        }
    }

    public static void addAutoAlarmClient(NettyClient... clients) {
        if (!enable) {
            logger.warn("mano.enable is not {}. Ignore addAutoAlarmClient", enable);
            return;
        }
        if (clients == null || clients.length == 0) {
            return;
        }
        Arrays.stream(clients).forEach(client -> {
            try {
                client.eventHandler
                        .addEventHandler(DISCOVERY_FAIL, clientEventHandler)
                        .addEventHandler(DISCOVERY_SUCCESS, clientEventHandler)
                        .addEventHandler(CONNECT_FAIL, clientEventHandler)
                        .addEventHandler(CONNECT_SUCCESS, clientEventHandler)
                        //
                        .addEventHandler(CONNECTION_UP, connectionEventHandler)
                        .addEventHandler(CONNECTION_DOWN, connectionEventHandler)
                        .addEventHandler(CONNECTION_WRITE_FAIL, connectionEventHandler)
                        .addEventHandler(CONNECTION_WRITE_SUCCESS, connectionEventHandler)
                        .addEventHandler(CONNECTION_INIT_SUCCESS, connectionEventHandler)
                        .addEventHandler(CONNECTION_INIT_FAIL, connectionEventHandler)
                        .addEventHandler(SERVICE_NAME_VERIFICATION_FAIL, connectionEventHandler)
                        .addEventHandler(TIMEOUT_ISOLATION, connectionEventHandler);
                ManoAlarmHelper.addClientResolveWatcher(client);
                logger.info("[MANO auto alarm client add]{id={}, host={}, port={}}", client.getId(), client.getHostName(), client.getPort());
            } catch (Throwable ex) {
                logger.error(ex, ex);
            }
        });
    }

    public static void removeAutoAlarmClient(NettyClient... clients) {
        if (!enable) {
            logger.warn("mano.enable is not {}. Ignore", enable);
            return;
        }
        if (clients == null || clients.length == 0) {
            return;
        }
        for (NettyClient client : clients) {
            try {
                client.eventHandler.removeEventHandler(DISCOVERY_FAIL, clientEventHandler).removeEventHandler(DISCOVERY_SUCCESS, clientEventHandler).removeEventHandler(CONNECT_FAIL, clientEventHandler).removeEventHandler(CONNECT_SUCCESS, clientEventHandler)
                        //
                        .removeEventHandler(CONNECTION_UP, connectionEventHandler).removeEventHandler(CONNECTION_DOWN, connectionEventHandler).removeEventHandler(CONNECTION_WRITE_FAIL, connectionEventHandler).removeEventHandler(CONNECTION_WRITE_SUCCESS, connectionEventHandler).removeEventHandler(CONNECTION_INIT_SUCCESS, connectionEventHandler).removeEventHandler(CONNECTION_INIT_FAIL, connectionEventHandler);
                logger.info("[MANO remove alarm client]{id={}, host={}, port={}}", client.getId(), client.getHostName(), client.getPort());
            } catch (Throwable ex) {
                logger.error(ex, ex);
            }
        }
    }


    /**
     * Kiểm tra đã notify thành công tới MANO hay chưa (Kiểm tra http response status = 204)
     */
    public static boolean isNotifyManoSuccess() {
        return ChassisConfig.ManoConfig.MANO_SUCCESS_CODES.contains(manoNotifyResultCode);
    }

    private static final Logger reloadLogger = LogManager.getLogger("com.viettel.vocs.reload");

    private static void registerAlarmSimulation() {
        APIReloadManager.getInstance().registerReloadAPI(new ReloadAPI() {
            @Override
            public String getReloadKey() {
                return "simulateThresholdAlarm";
            }

            @Override
            public String getReloadName() {
                return "simulateThresholdAlarm";
            }

            @Override
            public List<ReloadAPIParam> getListAPIParam() {
                List<ReloadAPIParam> lstParam = new ArrayList<>();
                ReloadAPIParam param = new ReloadAPIParam();
                param.setType("String");
                param.setDesc("Metric Mocks");
                param.setName("MetricMocks");
                param.setRequired(true);
                lstParam.add(param);

                return lstParam;
            }

            @Override
            public List<String> getListReloadType() {
                return Arrays.asList("DB", "File");
            }

            @Override
            public Pair<HttpResponseStatus, String> onReloadFromSql(Map<String, Object> map) {
                reloadLogger.info("===Received simulate threshold alarm request");
                String mockData = map.get("MetricMocks") == null ? null : map.get("MetricMocks").toString();
                if (!MetricMock.mock(mockData, reloadLogger)) {
                    reloadLogger.error("[simulateThresholdAlarm] Mock data incorrect format");
                    return Pair.of(HttpResponseStatus.INTERNAL_SERVER_ERROR, "Request invalid!!!");
                }
                return Pair.of(HttpResponseStatus.OK, "Request Accepted");
            }

            @Override
            public Pair<HttpResponseStatus, String> onReloadFromFile(Map<String, Object> map) {
                return Pair.of(HttpResponseStatus.NOT_FOUND, "Request not support");
            }
        });
    }
}

