package com.viettel.vocs.mano.service;

import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpAPI;
import com.viettel.vocs.microchassis.metrics.MetricCollector;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class MetricHandler extends HttpAPI {

	public MetricHandler(Handler<HttpServerContext> uh) {
		super(uh);
	}

	public static void toggleMetricHandle(HttpServerContext hctx) {
		if (!ManoIntegration.isMetricPushEnable()) {
			hctx.send("Metric push is not enabled. Ignore command", HttpResponseStatus.SERVICE_UNAVAILABLE);
			return;
		}
		Map<String, List<String>> inParams = hctx.getInParams();
		List<String> key = inParams.get("action");
		String message = "";
		if (key != null && !key.isEmpty()) {
			String s = key.get(0);
			if (s.equalsIgnoreCase("push")) {
				message = toggleMetricPush();
			}
			if (s.equalsIgnoreCase("cache")) {
				message = toggleMetricCache();
			}
		} else {
			message = toggleMetricPush();
			String cache = toggleMetricCache();
			if (cache != null && !cache.isEmpty()) message += "\n" + cache;
		}
		hctx.send(message);
	}

	public static String toggleMetricPush() {
		return String.format("Metric push is %s", ManoIntegration.toggleMetricPush() ? "disable" : "enable");
	}

	public static String toggleMetricCache() {
		return MetricCollector.mapMetrics.entrySet().stream()
			.map(metric -> String.format("Metric cache for %s is %s",
				metric.getKey(), metric.getValue().togglePauseCollector() ? "disable" : "enable")).collect(Collectors.joining("\n"));
	}

	public static boolean pullCheck(HttpServerContext hctx){
		if (ManoIntegration.isMetricPushEnable()) {
			hctx.send("Metric push is enable. Pull is not allowed", HttpResponseStatus.SERVICE_UNAVAILABLE);
			return false;
		} return true;
	}

	@Override
	public void handle(HttpServerContext ctx) {
		Map<String, List<String>> queryStringParams = ctx.getInParams();
		boolean export = queryStringParams.containsKey("export") || ctx.getInPath().contains("export");
		boolean toggle = queryStringParams.containsKey("toggle");
		boolean useHash = queryStringParams.containsKey("hash");
		boolean stat = queryStringParams.containsKey("stat");
		if (stat) {
			ctx.send(MetricCollector.report());
			return;
		}
		if (useHash) {
			ctx.send(ManoIntegration.toggleHashMetric() ? "Push hash metrics to MANO" : "Push raw metrics to MANO");
			return;
		}
		if (toggle) {
			if (!ManoIntegration.isMetricPushEnable()) {
				ctx.send("Metric push is not enabled. Ignore command", HttpResponseStatus.SERVICE_UNAVAILABLE);
				return;
			}
			List<String> key = queryStringParams.get("action");
			String message = "";
			if (key != null && !key.isEmpty()) {
				String s = key.get(0);
				if (s.equalsIgnoreCase("push")) {
					message = toggleMetricPush();
				}
				if (s.equalsIgnoreCase("cache")) {
					message = toggleMetricCache();
				}
			} else {
				message = toggleMetricPush();
				String cache = toggleMetricCache();
				if (cache != null && !cache.isEmpty()) {
					message += "\n" + cache;
				}
			}
			ctx.send(message);
			return;
		}
		if (export) {
			List<String> mode = queryStringParams.get("json");
			if (mode == null || mode.isEmpty()) {
				ManoIntegration.setDoPrintMetric(1);
			} else {
				ManoIntegration.setDoPrintMetric(2);
			}
			ctx.send();
			return;
		}
		if (!pullCheck(ctx)) return;
		if (userHander != null) {
			userHander.handle(ctx);
			return;
		}
		ctx.send(MetricCollector.report());
	}
}
