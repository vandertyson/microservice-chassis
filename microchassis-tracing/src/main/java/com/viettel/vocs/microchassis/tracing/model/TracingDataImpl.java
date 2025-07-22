package com.viettel.vocs.microchassis.tracing.model;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.tracing.client.TraceHandler;
import com.viettel.vocs.microchassis.tracing.utils.TracingUtils;
import io.jaegertracing.internal.JaegerTracer;
import io.jaegertracing.internal.clock.SystemClock;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMapAdapter;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class TracingDataImpl implements TracingData {
    private static final Logger logger = LogManager.getLogger(TracingDataImpl.class);
    private static final Logger customLogger = LogManager.getLogger("com.viettel.vocs.tracing");
    public static final Map<String, String> mapManoProp = new HashMap<>();

    private Map<String, Map<String, Long>> mapWaitLog = new ConcurrentHashMap<>(8);

    static {
        if (CommonConfig.InstanceInfo.VNFC_ID.get() != null) {
            mapManoProp.put("vnfc.id", CommonConfig.InstanceInfo.VNFC_ID.get());
        }
        if (CommonConfig.InstanceInfo.VNFC_NAME.get() != null) {
            mapManoProp.put("vnfc.name", CommonConfig.InstanceInfo.VNFC_NAME.get());
        }
        if (CommonConfig.InstanceInfo.VNF_INSTANCE_ID.get() != null) {
            mapManoProp.put("vnf.instance.id", CommonConfig.InstanceInfo.VNF_INSTANCE_ID.get());
        }
        if (CommonConfig.InstanceInfo.VNF_INSTANCE_NAME.get() != null) {
            mapManoProp.put("vnf.instance.name", CommonConfig.InstanceInfo.VNF_INSTANCE_NAME.get());
        }
        if (CommonConfig.InstanceInfo.VDU_NAME.get() != null) {
            mapManoProp.put("vdu.name", CommonConfig.InstanceInfo.VDU_NAME.get());
        }
    }

    private final Executor threadPool;

    public TracingDataImpl(Executor threadPool) {
        this.threadPool = threadPool;
        this.hashMapOfSpan = new ConcurrentHashMap<>();
    }

    public Executor getThreadPool() {
        return threadPool;
    }

    private ConcurrentMap<String, Span> hashMapOfSpan;
    @Getter @Setter private String msisdn;
    @Getter @Setter private String sessionID;
    @Getter @Setter private String serviceType;
    @Getter @Setter private RequestType requestType;
    private TraceHandler traceListener;
    private SpanContext parentSpanContext;
    @Getter @Setter
    private JaegerTracer tracer;
    public static final SystemClock clock = new SystemClock();

    private Span createOrGetSpan(ITracingOperation operation, SpanContext parentContext) {
        try {
            notifyTraceAction();
            String spanName = operation.getSpanName();
            if (hashMapOfSpan.containsKey(spanName)) {
                return hashMapOfSpan.get(spanName);
            }
            long l = clock.currentTimeMicros();
            JaegerTracer.SpanBuilder builder = tracer.buildSpan(spanName);
            if (parentContext != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Trace with parentContext. msisdn={}, session={}, request={}, parentTraceID={}, parentSpanID={}",
                            msisdn, sessionID, requestType, parentContext.toTraceId(), parentContext.toSpanId());
                }
                builder = builder.asChildOf(parentContext);
            }
            Span span = builder.withStartTimestamp(l).start();
            if (msisdn != null) {
                span.setTag("msisdn", msisdn);
            }
            if (sessionID != null) {
                span.setTag("sessionID", sessionID);
            }
            if (requestType != null) {
                span.setTag("requestType", requestType.getTagString());
            }
            if (serviceType != null) {
                span.setTag("serviceType", serviceType);
            }
            if (!mapManoProp.isEmpty()) {
                for (Map.Entry<String, String> entry : mapManoProp.entrySet()) {
                    span.setTag(entry.getKey(), entry.getValue());
                }
            }
            hashMapOfSpan.put(spanName, span);
            if (logger.isDebugEnabled()) {
                logger.debug("Span start. operation={} msisdn={} sessionID={} traceID={} spanID={} parent={}",
                        operation.getSpanName(), msisdn, sessionID, span.context().toTraceId(), span.context().toSpanId(),
                        parentContext != null ? parentContext.toTraceId() : null);
            }
            return span;
        } catch (Exception ex) {
            logger.error(ex, ex);
            return null;
        }
    }

    @Override
    public void start(ITracingOperation operation) {
        createOrGetSpan(operation, parentSpanContext);
    }

    @Override
    public void start(ITracingOperation operation, ITracingOperation parent) {
        try {
            Span parentSpan = createOrGetSpan(parent, null);
            createOrGetSpan(operation, parentSpan.context());
        } catch (Exception ex) {
            logger.error(ex, ex);
        }
    }

    @Override
    public void start(ITracingOperation operation, String parentContextID) {
        try {
            SpanContext parent = TracingUtils.extractSpanContext(parentContextID);
            createOrGetSpan(operation, parent);
        } catch (Exception ex) {
            logger.error(ex, ex);
        }
    }

    @Override
    public void tag(ITracingOperation operation, String key, String value) {
        Span span = hashMapOfSpan.get(operation.getSpanName());
        if (span != null && key != null && value != null) {
            notifyTraceAction();
            span.setTag(key, value);
        } else {
            logger.error("Invalid tag operation {} span {} key {} value {}", operation.getSpanName(), span, key, value);
        }
    }


    @Override
    public void log(ITracingOperation operation, String key, Supplier<String> value) {
        long l = System.currentTimeMillis();
        long baseTime = clock.currentTimeMicros();
        String spanName = operation.getSpanName();
        if (spanName == null) {
            return;
        }
        try {
            String parent = this.parentSpanContext != null ? this.parentSpanContext.toSpanId() : "null";
            Map<String, Long> logging = mapWaitLog.computeIfAbsent(operation.getSpanName(), s -> new HashMap<>());
            logging.put(key, l);
            CompletableFuture.runAsync(() -> {
                String result;
                long t1 = System.currentTimeMillis();
                try {
                    result = value.get();
                    if (result == null) {
                        result = "null";
                    }
                } catch (Throwable ex) {
                    logger.error(ex, ex);
                    result = "Exception: " + ex.getMessage();
                }
                long completeTime = System.currentTimeMillis();
                Long executeTime = completeTime - t1;
                logToSpan(operation, baseTime, l, key, value.get());
                Long remove = logging.remove(key);
                if (logger.isDebugEnabled()) {
                    long duration = remove != null ? System.currentTimeMillis() - remove : -1;
                    long done = remove != null ? completeTime - remove : -1;
                    logger.debug("Async log trace complete. parent={}, span={}, key={}, " +
                                    "resultLen={}, executeMs={}, completeMs={}, durationMs={}",
                            parent, spanName, key, result.getBytes(StandardCharsets.UTF_8).length, executeTime, done, duration);
                }
                if (customLogger.isTraceEnabled()) {
                    logger.trace("[{}] {}", key, result);
                }
            }, threadPool);
        } catch (Throwable ex) {
            logger.error(ex, ex);
            logToSpan(operation, baseTime, l, key, value.get());
        }
    }

    private void logToSpan(ITracingOperation operation, Long baseTime, long timeStamp, String key, String result) {
        try {
            String spanName = operation.getSpanName();
            if (spanName == null) {
                return;
            }
            Span span = hashMapOfSpan.get(spanName);
            if (span == null) {
                return;
            }
            notifyTraceAction();
            Map<String, String> logs = new HashMap<>();
            String format = TimeUtils.miliToString(timeStamp);
            String keyWithTime = "[" + format + "] " + key;
            logs.put(keyWithTime, result);
            span.log(baseTime, logs);
            if (logger.isDebugEnabled()) {
                String parent = this.parentSpanContext != null ? this.parentSpanContext.toSpanId() : "null";
                logger.debug("Log to span. traceId={} spanId={} parent={}, operation={}, key={}",
                        span.context().toTraceId(), span.context().toSpanId(), parent, spanName, key);
            }
            if (customLogger.isTraceEnabled()) {
                logger.trace("[{}] {}", key, result);
            }
        } catch (Exception ex) {
            logger.error(ex, ex);
        }
    }

    private void notifyTraceAction() {
        if (traceListener != null) {
            if (msisdn != null) {
                traceListener.onMsisdnTrace(msisdn);
            }
            if (sessionID != null) {
                traceListener.onSessionTrace(sessionID);
            }
        }
    }


    @Override
    public void log(ITracingOperation operation, String key, String value) {
        long baseTime = clock.currentTimeMicros();
        logToSpan(operation, baseTime, System.currentTimeMillis(), key, value);
    }

    @Override
    public void finish(ITracingOperation operation) {
        String spanName = operation.getSpanName();
        Long l = clock.currentTimeMicros();
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Long> remove = mapWaitLog.remove(spanName);
                    if (remove != null) {
                        int countWait = remove.size();
                        if (countWait > 0) {
                            long maxWaitMs = countWait * ChassisConfig.TracingConfig.MAX_LOG_ASYNC_WAIT.get();
                            long l1 = System.currentTimeMillis();
                            if (logger.isDebugEnabled()) {
                                logger.debug("Tracing wait for async log. count={}, maxWaitMs={}", countWait, maxWaitMs);
                            }
                            boolean complete = true;
                            while (remove.size() > 0) {
                                if (System.currentTimeMillis() - l1 > maxWaitMs) {
                                    complete = false;
                                    break;
                                }
                                TimeUtils.waitSafeMili(10);
                            }
                            if (logger.isDebugEnabled()) {
                                logger.debug("Tracing stop wait for async log. count={}, complete={}", remove.size(), complete);
                            }
                            finishSpan(spanName, l);
                        } else {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Operation complete with all async log complete. spanName={}", spanName);
                            }
                            finishSpan(spanName, l);
                        }
                    } else {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Operation complete without wait. spanName={}", spanName);
                        }
                        finishSpan(spanName, l);
                    }
                } catch (Throwable ex) {
                    logger.error(ex, ex);
                }
            }, threadPool);
        } catch (Throwable ex) {
            logger.error(ex, ex);
        }
    }

    private void finishSpan(String spanName, Long timestamp) {
        Span span = hashMapOfSpan.get(spanName);
        if (span != null) {
            span.finish(timestamp);
            mapWaitLog.remove(spanName);
            if (logger.isDebugEnabled()) {
                logger.debug("Span finish. traceId={}, spanId={}, operation={}",
                        span.context().toTraceId(), span.context().toSpanId(), spanName);
            }
        }
    }

    @Override
    public String getContextID(ITracingOperation operation) {
        Span span = hashMapOfSpan.get(operation.getSpanName());
        if (span != null) {
            TextMapAdapter textMap = TracingUtils.spanContextToTextMap(span.context());
            for (Map.Entry<String, String> entry : textMap) {
                if (entry.getValue() != null) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    public Span getSpan(String spanName) {
        return hashMapOfSpan.get(spanName);
    }

    public void setTraceListener(TraceHandler traceListener) {
        this.traceListener = traceListener;
    }

    public TraceHandler getTraceListener() {
        return traceListener;
    }

    public void setParentSpanContext(SpanContext parentSpanContext) {
        this.parentSpanContext = parentSpanContext;
    }

    public SpanContext getParentSpanContext() {
        return parentSpanContext;
    }
}
