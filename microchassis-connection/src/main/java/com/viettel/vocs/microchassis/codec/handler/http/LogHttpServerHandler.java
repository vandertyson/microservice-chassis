package com.viettel.vocs.microchassis.codec.handler.http;

import com.viettel.vocs.common.log.MetricsBenchmark;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;

/**
 * @author vttek
 */
public abstract class LogHttpServerHandler extends HttpServerHandler {
	protected static final Logger logger = LogManager.getLogger(LogHttpServerHandler.class);

	public LogHttpServerHandler(HttpServerOptions config) {
		super(config);
	}

	@Override
	public void log(HttpServerContext ctx) {
		String toString = ctx.getInMsg().toString(StandardCharsets.UTF_8);
		logger.debug(String.format("[FastReturn Receive request]{ctx=%s, http2=%s, headers=%s, content=%s}",
			ctx.getChannelString(), ctx.isHttp2(), ctx.getInMsg().headers(), toString));
		MetricsBenchmark.getInstance().statisticMetris(System.nanoTime(), ctx.getInMsg().readableBytes(), "Server");
	}
}
