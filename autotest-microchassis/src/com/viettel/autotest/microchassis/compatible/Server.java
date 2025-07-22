/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.autotest.microchassis.compatible;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.log.MetricsBenchmark;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.context.ChassisReplyContext;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.connection.config.SslConfiguration;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import com.viettel.vocs.microchassis.tcp.server.TcpServer;
import io.netty.buffer.ByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jctools.queues.MpmcArrayQueue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

/**
 * @author vttek
 */
public class Server {

	static final Logger logger = LogManager.getLogger(Server.class);
	static Integer port = Integer.getInteger("port", 8088);
	static Boolean ssl = Boolean.getBoolean("ssl");
	static String keyPath = System.getProperty("keyPath", "./etc/key_nodes.pem");
	static String certPath = System.getProperty("certPath", "./etc/cert_nodes.pem");
	static String mode = System.getProperty("mode", "tcp");
	static final MpmcArrayQueue<TcpContext> queue = new MpmcArrayQueue<>(CommonConfig.QueueConfig.DEFAULT_QUEUE_SIZE.getInt());

	public static void main(String[] args) throws Exception {
		LogUtils.setupLog4j2();
		final ExecutorService x = Executors.newFixedThreadPool(Integer.getInteger("numprocess", 5));
		if (Boolean.getBoolean("enqueue")) {
			for (int i = 0; i < Integer.getInteger("numprocess", 5); i++) {
				Executors.newSingleThreadExecutor().submit((Runnable) () -> {
					while (true) {
						try {
							TcpContext poll = queue.poll();
							if (poll != null) {
								LockSupport.parkNanos(Long.getLong("sleep.micro", 300) * 1_000);
								processRequest(poll);
								poll.decompose();
							} else {
								LockSupport.parkNanos(1000);
							}
						} catch (Exception ex) {
							logger.error(ex, ex);
						}
					}
				});
			}
		}
		if (mode.equals("tcp")) {
			TcpServer server = new TcpServer(new ServerConfiguration("server", port));
			server.start(new TcpHandler(server.getConfig()) {
				@Override
				public void handle(TcpContext tctx) {
					if (Boolean.getBoolean("enqueue")) {
						x.execute(() -> {
							LockSupport.parkNanos(Long.getLong("sleep.micro", 300) * 1_000);
							processRequest(tctx);
						});
					} else {
						processRequest(tctx);
					}
				}
			});
			server.waitServerClose();
			return;
		}
		if (mode.equals("old")) {
			TcpServer server = new TcpServer(new ServerConfiguration("default", port));
			server.start(new TcpHandler(server.getConfig()) {
				@Override
				public void handle(TcpContext tcpContext) {
					Msg inMsg = tcpContext.getInMsg();
					try {
						Msg response = tcpContext.getCoMsg();
						tcpContext.echo();
						tcpContext.send(response, response);
						MetricsBenchmark.getInstance().statisticMetris(TimeUtils.nowNano(), inMsg.readableBytes(), "Response");
					} catch (Exception ex) {
						logger.error(ex, ex);
					}
//					requestData.release();
				}
			});
			return;
		}
		if (mode.equals("http")) {
			HttpServerOptions httpServerOptions = new HttpServerOptions("http", port);
			if (ssl) {
				httpServerOptions.setSslConfiguration(new SslConfiguration().setCertPath(certPath).setKeyPath(keyPath));
			}
			HttpServer server = new HttpServer(httpServerOptions);
			server.start(new HttpServerHandler(server.getConfig()) {
				@Override
				public void handle(HttpServerContext ctx) {
					try {
						ByteBuf data = ctx.getInMsg().getReadOnlyContentByteBuf();
						ctx.send(data);
						ctx.decompose();
						MetricsBenchmark.getInstance().statisticMetris(System.nanoTime(), data.readableBytes(), "Response");
					} catch (Exception ex) {
						logger.error(ex, ex);
					}
				}
			});
			server.waitServerClose();
		}
	}

	private static void processRequest(ChassisReplyContext<Msg, Msg> rctx) {
		byte[] data = rctx.getInMsg().getContent();
		rctx.getInMsg().decompose();
		try {
			rctx.send(data);
			rctx.decompose();
			MetricsBenchmark.getInstance().statisticMetris(System.nanoTime(), data.length, "Response");
		} catch (Exception e) {
			MetricsBenchmark.getInstance().statisticMetris(System.nanoTime(), data.length, "SendError");
		}

	}

}
