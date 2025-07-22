package com.viettel.vocs.microchassis.tracing.utils;

import com.viettel.vocs.microchassis.codec.context.ChassisReplyContext;
import com.viettel.vocs.microchassis.codec.context.ReplyContext;
import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import com.viettel.vocs.microchassis.tracing.client.TracingMonitor;
import com.viettel.vocs.microchassis.tracing.model.ITracingOperation;
import com.viettel.vocs.microchassis.tracing.TracingConfiguration;
import com.viettel.vocs.microchassis.tracing.model.TracingData;
import com.viettel.vocs.microchassis.tracing.model.TracingDataImpl;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http2.Http2Headers;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMapAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.viettel.vocs.microchassis.tracing.utils.TracingUtils.SPAN_CONTEXT_KEY;

public class TraceHelper {
    private TraceHelper(){}
    private final static Logger logger = LogManager.getLogger("com.viettel.vocs.microchassis.tracing");

    public static void inject(TracingData tracing, ITracingOperation operation, Msg msg) {
        if (tracing == null) {
            logger.error("Can not find TracingData. Injection canceled. operation={}", operation);
            return;
        }
        if (tracing instanceof TracingDataImpl) {
            TracingDataImpl impl = (TracingDataImpl) tracing;
            Span span = impl.getSpan(operation.getSpanName());
            if (span == null) {
                logger.error("Can not find span. Injection canceled." +
                        "operation={}, msisdn={}", operation, tracing.getMsisdn());
                return;
            }
            SpanContext spanContext = span.context();
            TextMapAdapter textMap = TracingUtils.spanContextToTextMap(spanContext);
            for (Map.Entry<String, String> entry : textMap) {
                msg.headers().set(SPAN_CONTEXT_KEY, entry.getValue());
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Tracing inject header. operation={}, textMap={{}}, headers={}",
                        operation.getSpanName(),
                        StreamSupport.stream(textMap.spliterator(), false)
                                .map(f -> f.getKey() + "=" + f.getValue())
                                .collect(Collectors.joining(","))
                        , msg.headers());
            }
        }
    }

    private static SpanContext getSpanContext(ChassisReplyContext<Msg,Msg> parentContext) {
        try {
            String context = parentContext.getInMsg().headers().getString(SPAN_CONTEXT_KEY);
            SpanContext spanContext = TracingUtils.extractSpanContext(context);
            if (logger.isDebugEnabled()) {
                logger.debug("Tracing extract span context. headerValue={}, extractedTraceID={}",
                        context, spanContext != null ? spanContext.toTraceId() : null);
            }
            return spanContext;
        } catch (Throwable ex) {
            logger.error(ex, ex);
            return null;
        }
    }

    private static SpanContext getSpanContext(Msg msg) {
        return msg.headers().isEmpty() ? null : TracingUtils.extractSpanContext(msg.headers().getString(SPAN_CONTEXT_KEY));
    }

    public static boolean hasTracingContext(ChassisReplyContext ctx) {
        SpanContext spanContext = getSpanContext(ctx);
        return spanContext != null;
    }

    public static boolean hasTracingContext(Msg msg) {
        SpanContext spanContext = getSpanContext(msg);
        return spanContext != null;
    }

    /*
     * inject SpanContext to HttpHeader
     */
    public static FullHttpRequest injectContextToHeader(SpanContext spanContext, FullHttpRequest request) {
        if (spanContext == null) {
            return request;
        }

        TextMapAdapter textMap = TracingUtils.spanContextToTextMap(spanContext);
        for (Map.Entry<String, String> entry : textMap) {
            request.headers().add(SPAN_CONTEXT_KEY, entry.getValue());
        }

        return request;
    }

    /*
     * extract SpanContext from HttpHeader/Msg
     */
    public static SpanContext extractSpanContextFromHeader(final Http2Headers header) {
        CharSequence spanContextValue = header.get(SPAN_CONTEXT_KEY);
        return TracingUtils.extractSpanContextFromCharSequence(spanContextValue);
    }

    public static SpanContext extractSpanContextFromHeader(final HttpHeaders header) {
        CharSequence spanContextValue = header.get(SPAN_CONTEXT_KEY);
        return TracingUtils.extractSpanContextFromCharSequence(spanContextValue);
    }

    public static SpanContext extractSpanContextFromHeader(final Msg msg) {
        CharSequence spanContextValue = (CharSequence) msg.headers().get(SPAN_CONTEXT_KEY);
        return TracingUtils.extractSpanContextFromCharSequence(spanContextValue);
    }

    public static TracingConfiguration getParameterReq(String rawData) {
        return JsonUtils.getDecoder().fromJson(rawData, TracingConfiguration.class);
    }

    public static TracingData createTracingData(ReplyContext ctx) {
        String requestHeaderByKey = ctx.getInMsg().getStringHeaders().get(SPAN_CONTEXT_KEY);
        if (requestHeaderByKey != null) {
            return TracingMonitor.getInstance().create(requestHeaderByKey);
        }
        return TracingMonitor.getInstance().create();
    }

    public static TracingData createTracingData(Msg msg) {
        String s = msg.headers().getString(SPAN_CONTEXT_KEY);
        if (s == null) {
            return TracingMonitor.getInstance().create();
        }
        return TracingMonitor.getInstance().create(s);
    }

}
