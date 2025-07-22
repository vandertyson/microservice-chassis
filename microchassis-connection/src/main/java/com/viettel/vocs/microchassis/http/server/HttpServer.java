/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.http.server;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.LBRAdapter;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2PingFrame;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.ssl.SslContext;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4JLoggerFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import java.io.File;
import java.util.function.BooleanSupplier;
import java.util.function.IntUnaryOperator;

public class HttpServer extends NettyServer<HttpServerOptions, HttpServerHandler, HttpServerInitializer> {
	private static final Logger logger = LogManager.getLogger(HttpServer.class);
	protected final SslContext sslCtx;
	@Override
	public LBRAdapter getNegtor() {
		return defaultStlnLBRA;
	}

	public HttpServer(HttpServerOptions options) throws Exception {
		super(options);
		sslCtx = config.sslConfiguration != null ? config.sslConfiguration.build(true) : null;
	}

	@Override
	public HttpServer restart(HttpServerHandler handlers) throws Exception {
		return (HttpServer) super.restart(handlers);
	}

	@Override
	public HttpServer start(HttpServerHandler handlers) throws Exception {
		return (HttpServer) super.start(handlers);
	}

	private void notifyStop(IntUnaryOperator lastStreamIdProcessor) {
		super.notifyStop();
		if (isInitialized() /*started*/) {
			logger.info("HttpServer process notifyStop for HTTP/2");
			connectedChannels.forEach(channel -> {
				Http2ConnectionHandler handler = channel.pipeline().get(Http2ConnectionHandler.class);
				byte[] msg = "ServerNotify stop".getBytes();
				if (handler != null) {
					ChannelHandlerContext ctx = channel.pipeline().lastContext();
					handler.goAway(ctx,
						lastStreamIdProcessor.applyAsInt(handler.connection().remote().lastStreamCreated()),
						Http2Error.NO_ERROR.code(), bytebufAllocator.buffer().ensureWritable(msg.length).writeBytes(msg), channel.newPromise().addListener(future -> {
							// do nothing
						})); //actively send goAway all
					ctx.writeAndFlush(new DefaultHttp2PingFrame(0, true)); // must flush a ping to deliver GO_AWAY frame
				}
			});
		}
	}

	public void notifyStop2Stage(BooleanSupplier businessQualifier) {
		long t = TimeUtils.nowNano();
		notifyStop(i -> Integer.MAX_VALUE);
		while (businessQualifier.getAsBoolean()
			&& TimeUtils.miliPassed(t) < 10000)
			TimeUtils.waitSafeMili(100);
		logger.info("=================finish notify stop stage 1 MAX_INT==================");
		notifyStop();
		TimeUtils.waitSafeMili(1000);
		logger.info("=================finish notify stop stage 2 lastStreamId==================");
	}

	public void notifyStopIncremental(int nextAllowMsg) {
		notifyStop(i -> nextAllowMsg >= (Integer.MAX_VALUE - 1) / 2
			? Integer.MAX_VALUE // case accept any streamId next
			: (nextAllowMsg > 0
			? i + 2 * nextAllowMsg // case incremental
			: i)); // case 0
	}

	@Override
	public void notifyStop() {
		notifyStopIncremental(0);
	}

	@Override
	protected HttpServerInitializer newServerInitializer(HttpServerHandler handler) {
		return new HttpServerInitializer(this, sslCtx, handler);
	}

	public static void main(String[] args) throws Exception {
		InternalLoggerFactory.setDefaultFactory(Log4JLoggerFactory.INSTANCE);
		String log4jFile = System.getProperty("log4j.configurationFile");
		if (log4jFile == null || !(new File(log4jFile)).exists()) {
			LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
			File log4j = new File("./etc/log4j2.xml");
			ctx.setConfigLocation(log4j.toURI());
		}
		HttpServerOptions options = new HttpServerOptions("test", 9999);
//			options.enable(ChassisConst.SupportVersion.HTTP_2_0);
		HttpServer server = new HttpServer(options);
		server.start(new HttpServerHandler(server.getConfig()) {
			public void handle(HttpServerContext ctx) {
				try {
					HttpRequest inMsg = ctx.getInMsg();
					ctx.getCoMsg().headers().set("location", "http://172.20.3.58:3868/npcf-smpolicycontrol/v2/sm-policies/smpolicy1745643392489_84320889002_452040931453410_v-internet");
					ctx.getCoMsg().headers().set("content-type", "application/json");
					ctx.getCoMsg().headers().set("smpolicy_id", "http://172.20.3.58:3868/npcf-smpolicycontrol/v2/sm-policies/smpolicy1745643392489_84320889002_452040931453410_v-internet");
					ctx.getCoMsg().headers().set(ChassisConst.CustomHeader.msgIdHeader, "685379ee-f47b-4edc-81e3-f86ef3");
					ctx.send("{\"status\":404,\"detail\":\"USER_UNKNOWN\",\"chargingInfo\":{\"primaryChfAddress\":\"http://10.205.8.209:8765\",\"secondaryChfAddress\":\"http://10.205.8.209:8765\"}}", HttpResponseStatus.NOT_FOUND);
				} catch (Exception e) {
					logger.error(e, e);
				}
			}
		});
	}
}
