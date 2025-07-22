package com.viettel.vocs.microchassis.tracing;

import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.microchassis.tracing.client.TracingMonitor;
import com.viettel.vocs.microchassis.tracing.utils.TraceHelper;
import com.viettel.vocs.microchassis.tracing.utils.TracingUtils;
import io.jaegertracing.internal.JaegerTracer;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMapAdapter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestTracingHelper {
    public static String SERVICE_NAME = "testService";
    public static String SPAN_NAME = "testSpan";

    @BeforeAll
    public static void setUp() {
        System.out.println("----------------------> setUp");
        try {
            TracingMonitor.getInstance()
                    .withServiceName(SERVICE_NAME)
                    .startTracing();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @AfterAll
    public static void release() {
        TracingMonitor.getInstance().stopTracing();
    }

    @Test
    public void testInitJaegerTracer() {
        System.out.println("---> testInitJaegerTracer");

        try (JaegerTracer tracer = TracingUtils.initJaegerTracer(SERVICE_NAME)) {
            Assertions.assertNotNull(tracer);
        }
    }

    @Test
    public void testSpanContextToTextMap() {
        Span span = TracingMonitor.getInstance().getTracer().buildSpan(SPAN_NAME).start();
        TextMapAdapter textMap = TracingUtils.spanContextToTextMap(span.context());

        Assertions.assertNotNull(textMap);
    }

    @Test
    public void testTextMapToSpanContext() {
        Span spanParent = TracingMonitor.getInstance().getTracer().buildSpan(SPAN_NAME).start();
        // Span context value: trace_id:span_id:parent_id:int_flag
        System.out.println("Parent span: " + spanParent.context().toString());

        Span span = TracingMonitor.getInstance().getTracer().buildSpan(SPAN_NAME).asChildOf(spanParent).start();
        TextMapAdapter textMap = TracingUtils.spanContextToTextMap(span.context());

        SpanContext spanContext = TracingUtils.textMapToSpanContext(textMap);

        System.out.println("Child span: " + spanContext.toString());
        Assertions.assertNotNull(spanContext);
        Assertions.assertEquals(span.context().toString(), spanContext.toString());
    }

    @Test
    public void testInjectContextToHeader() {
        System.out.println("---> testInjectContextToHeader");

        Span span = TracingMonitor.getInstance().getTracer().buildSpan(SPAN_NAME).start();
        String expected = span.context().toString();

        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost:8080");
        TraceHelper.injectContextToHeader(span.context(), request);

        String actual = request.headers().get(TracingUtils.SPAN_CONTEXT_KEY);

        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void testExtractSpanContextFromHeader() {
        System.out.println("---> testExtractSpanContextFromHeader");

        Span span = TracingMonitor.getInstance().getTracer().buildSpan(SPAN_NAME).start();
        SpanContext expected = span.context();

        String value = expected.toString();
        // trace_id:span_id:parent_id:int_flag
        System.out.println("Span string value: " + value);

        DefaultHttp2Headers header = new DefaultHttp2Headers();
        header.add(TracingUtils.SPAN_CONTEXT_KEY, value);

        SpanContext actual = TraceHelper.extractSpanContextFromHeader(header);

        Assertions.assertNotNull(actual);
        Assertions.assertEquals(expected.toString(), actual.toString());

        // -----
        header = new DefaultHttp2Headers();
        actual = TraceHelper.extractSpanContextFromHeader(header);

        Assertions.assertNull(actual);
    }

    @Test
    public void testGetParameterReq() {
        System.out.println("---> testGetParameterReq");

        TracingConfiguration parameter = new TracingConfiguration();
        String expected = JsonUtils.getEncoder().toJson(parameter);
        TracingConfiguration parameter1 = TraceHelper.getParameterReq(expected);

        String actual = JsonUtils.getEncoder().toJson(parameter1);
        Assertions.assertEquals(expected, actual);
    }
}
