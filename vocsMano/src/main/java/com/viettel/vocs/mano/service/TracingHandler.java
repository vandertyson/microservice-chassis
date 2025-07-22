package com.viettel.vocs.mano.service;

import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpAPI;
import com.viettel.vocs.microchassis.tracing.client.TracingMonitor;
import com.viettel.vocs.microchassis.tracing.TracingConfiguration;
import com.viettel.vocs.microchassis.tracing.utils.TraceHelper;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;

class TracingHandler extends HttpAPI {

	@Override
	public void putHandle(HttpServerContext ctx) {
		logger.info("Tracing config modify. ctx={}, isHttp2={}", ctx.getChannelString(), ctx.isHttp2());
		Map<String, List<String>> inParams = ctx.getInParams();
		List<String> max = inParams.get("max");
		if (max != null && !max.isEmpty()) {
			try {
				int val = Integer.parseInt(max.get(0));
				TracingMonitor.getInstance().setMaxTracingSize(val);
				ctx.send("max tracing object is set to " + val);
			} catch (Exception ex) {
				logger.error(ex, ex);
				ctx.send(ex.getMessage(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
			}
		}
		ctx.send(HttpResponseStatus.NO_CONTENT);
	}

	@Override
	public void getHandle(HttpServerContext ctx) {
		if (logger.isDebugEnabled()) {
			logger.debug("Tracing config get.ctx={}, isHttp2={}",
				ctx.getChannelString(), ctx.isHttp2());
		}
		ctx.send(TracingMonitor.getTracingConfig());
	}

	@Override
	public void postHandle(HttpServerContext ctx) {
		String payload = ctx.getInMsg().toString(CharsetUtil.UTF_8);
		logger.info("Tracing config update. ctx={}, headers={}, payload={}",
			ctx.getChannelString(),
			ctx.getInMsg().headers(),
			payload);
		try {
			TracingConfiguration parameterNew = TraceHelper.getParameterReq(payload);
			Pair<HttpResponseStatus, String> result = TracingMonitor.getInstance().updateParameter(parameterNew);
			if (HttpResponseStatus.OK.equals(result.getKey())) {
				ctx.send(TracingMonitor.getTracingConfig());
			} else {
				ctx.send(result.getValue(), result.getKey());
			}
		} catch (Exception ex) {

			logger.error(ex, ex);
			ctx.send(ex.getMessage(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Override
	public void handle(HttpServerContext ctx) {
		if (ctx.getInParams().containsKey("stat")) ctx.send(TracingMonitor.getInstance().toString());
		else dispatch(ctx);
	}
}
