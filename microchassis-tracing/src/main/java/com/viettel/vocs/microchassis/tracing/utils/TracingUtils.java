package com.viettel.vocs.microchassis.tracing.utils;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import io.jaegertracing.Configuration;
import io.jaegertracing.Configuration.ReporterConfiguration;
import io.jaegertracing.Configuration.SamplerConfiguration;
import io.jaegertracing.internal.JaegerSpanContext;
import io.jaegertracing.internal.JaegerTracer;
import io.jaegertracing.internal.propagation.B3TextMapCodec;
import io.jaegertracing.internal.propagation.TextMapCodec;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMapAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;

public class TracingUtils {
    private TracingUtils() {
    }

    // Don't change this value
    public static final String SPAN_CONTEXT_KEY = "uber-trace-id";

    private static final Logger logger = LogManager.getLogger(TracingUtils.class);

    /*
     * initTracer
     */
    public static JaegerTracer initJaegerTracer(String inputServiceName) {
        String vnf = CommonConfig.InstanceInfo.VNF_INSTANCE_NAME.get();
        String overideServiceName = null;
        if (vnf != null) {
            try {
                String[] split = vnf.split("-");
                overideServiceName = split[0].toUpperCase();
                logger.info("Override tracing service name. vnf={}, serviceName={}", vnf, overideServiceName);
            } catch (Throwable ex) {
                logger.error(ex, ex);
            }
        }
        String serviceName = overideServiceName != null ? overideServiceName : inputServiceName;

        if (CommonConfig.InstanceInfo.HOST_IP.get() == null || ChassisConfig.TracingConfig.JAEGER_AGENT_PORT.get() == null) {
            System.setProperty("JAEGER_ENDPOINT", ChassisConfig.TracingConfig.JAEGER_ENDPOINT.get());
        } else {
            System.setProperty("JAEGER_AGENT_HOST", CommonConfig.InstanceInfo.HOST_IP.get());
            System.setProperty("JAEGER_AGENT_PORT", String.valueOf(ChassisConfig.TracingConfig.JAEGER_AGENT_PORT));
        }
        SamplerConfiguration samplerConfig = SamplerConfiguration.fromEnv()
                .withType(ConstSampler.TYPE)
                .withParam(1);
        Configuration.SenderConfiguration sender = Configuration.SenderConfiguration.fromEnv();
        ReporterConfiguration reporterConfig = ReporterConfiguration.fromEnv().withSender(sender);

        Configuration config = new Configuration(serviceName)
                .withSampler(samplerConfig)
                .withReporter(reporterConfig)
                .withTraceId128Bit(true);

        JaegerTracer tracer = config.getTracer();
        logger.info("Init tracer. agentHost={}, agentPort={}, collector={}, tracer={}",
                CommonConfig.InstanceInfo.HOST_IP, ChassisConfig.TracingConfig.JAEGER_AGENT_PORT, ChassisConfig.TracingConfig.JAEGER_ENDPOINT, tracer.toString());
        return tracer;
    }

    /*
     * B3-TextMap format
     */
    public static TextMapAdapter spanContextToB3TextMap(SpanContext spanContext) {
        TextMapAdapter textMap = new TextMapAdapter(new HashMap<>());
        new B3TextMapCodec.Builder().build().inject((JaegerSpanContext) spanContext, textMap);

        return textMap;
    }

    public static SpanContext b3TextMapToSpanContext(TextMapAdapter textMap) {
        return new B3TextMapCodec.Builder().build().extract(textMap);
    }

    /*
     * TextMap format
     */
    public static TextMapAdapter spanContextToTextMap(SpanContext spanContext) {
        TextMapAdapter textMap = new TextMapAdapter(new HashMap<>());
        TextMapCodec.builder().withSpanContextKey(SPAN_CONTEXT_KEY).build().inject((JaegerSpanContext) spanContext, textMap);
        return textMap;
    }

    public static SpanContext textMapToSpanContext(TextMapAdapter textMap) {
        return TextMapCodec.builder().withSpanContextKey(SPAN_CONTEXT_KEY).build().extract(textMap);
    }

    public static SpanContext extractSpanContextFromCharSequence(CharSequence spanContextValue) {
        if (spanContextValue == null) {
            return null;
        }
        String s = spanContextValue.toString();
        if (s.isBlank() || s.isEmpty()) {
            return null;
        }
        return extractSpanContext(s);
    }

    public static SpanContext extractSpanContext(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        TextMapAdapter textMap = new TextMapAdapter(new HashMap<>());
        textMap.put(TracingUtils.SPAN_CONTEXT_KEY, value);
        return TracingUtils.textMapToSpanContext(textMap);
    }
}
