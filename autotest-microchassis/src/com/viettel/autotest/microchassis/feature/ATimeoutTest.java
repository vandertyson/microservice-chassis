package com.viettel.autotest.microchassis.feature;

import com.viettel.autotest.microchassis.lib.generator.PayloadGenerator;
import com.viettel.vocs.common.IDfy;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.os.thread.JobWithTimeout;
import com.viettel.vocs.common.os.thread.SharedThreadPoolManager;
import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.RequestTimeoutMonitor;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import com.viettel.vocs.microchassis.tcp.client.TcpClient;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import com.viettel.vocs.microchassis.tcp.server.TcpServer;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class ATimeoutTest{
	protected static final Logger logger = LogManager.getLogger(ATimeoutTest.class);

	@BeforeAll
	public void setUpClass(){
		LogUtils.setupLog4j2();
	}
	@Test
	void testHttp1Timeout() {
		AtomicLong countSuccess = new AtomicLong();
		AtomicLong countTimeout = new AtomicLong();
		Set<String> timeout = new HashSet<>();
		long timeoutMs = 5000;
		try {
			HttpServer server = new HttpServer(new HttpServerOptions("test-server", 14567));
			server.start(new HttpServerHandler(server.getConfig()){
				public void handle(HttpServerContext ctx){
					SharedThreadPoolManager.executeAsync(new JobWithTimeout() {
						@Override
						public String getName() {
							String internalRequestID = ctx.getInID();
							return "Server response " + internalRequestID;
						}

						@Override
						public void run() {
							try {
								TimeUtils.waitSafeMili(timeoutMs + 1000);
								ctx.send("OK");
							} catch (Exception ex) {
								logger.info(ex, ex);
							}
						}
					});
				}});
			logger.info("SERVER STARTED");
			TimeUtils.waitSafeMili(1000);
			HttpClientOptions clientOptions = new HttpClientOptions("localhost", 14567, "test-http1");
			clientOptions.setSendTimeoutMs(timeoutMs);
			HttpClient client = clientOptions.newClientHttp1Upgrade();
			client.start(new HttpClientHandler(clientOptions){
				public void handle(HttpClientReceiveContext h1ctx) {
					countSuccess.incrementAndGet();
					logger.info("Receive async http1 response. msg={}", h1ctx.getInMsg());
				}
				@Override
				public void timeoutHandle(String requestID) {
					countTimeout.incrementAndGet();
					logger.info("Timeout http1 request " + requestID);
					timeout.add(requestID);
				}
			});
			HttpRequest request = client.createReq(HttpMethod.POST,PayloadGenerator.testUrl,  "hello".getBytes());
			String requestID = IDfy.generateNewId();
			client.send(request, requestID);
			TimeUtils.waitSafeMili(timeoutMs + 100);
			assertTrue(timeout.contains(requestID));

			//syncTimeout
			HttpRequest rq2 = client.createReq(HttpMethod.POST, PayloadGenerator.testUrl, "hello".getBytes());
			try {
				client.sendSync(rq2);
			} catch (Exception ex) {
				assertTrue(ex instanceof TimeoutException);
				logger.error(ex, ex);
			}
			TimeUtils.waitSafeMili(5000);
			client.close();
			server.stop();
		} catch (Exception ex) {
			logger.error(ex, ex);
			fail(ex);
		}
	}

	@Test
	void tcpTimeoutTest() throws Exception {
		TcpServer server = new TcpServer(new ServerConfiguration("test", 13456));
		AtomicInteger clientDone = new AtomicInteger(0);
		AtomicInteger serverDone = new AtomicInteger(0);
		AtomicInteger timeoutTrigger = new AtomicInteger(0);
		AtomicInteger handleTrigger = new AtomicInteger(0);
		server.start(new TcpHandler(server.getConfig()){
			public void handle(TcpContext tctx){
				CompletableFuture.runAsync(() -> {
					try {
						TimeUtils.waitSafeMili(2000);
						tctx.send("OK");
						serverDone.incrementAndGet();
					} catch (Exception e) {
						fail(e.getMessage());
					}
				});
			}});
		logger.info("Server started");
		TcpClient client = new TcpClient(new ClientConfiguration("127.0.0.1", 13456, "okclient").setSendTimeoutMs(1000));
		ConnectionManager.getInstance().startMonitorThread();
		client.start(new TcpHandler(client.getConfig()) {

			@Override
			public void dropHandle(Msg failedMsg, boolean isSent) {
				if (isSent) clientDone.incrementAndGet();
				logger.info("Receive self drop for response {}", failedMsg);
			}

			@Override
			public void handle(TcpContext tctx) {
				logger.info("Receive response {}", tctx.getInMsg());
				clientDone.incrementAndGet();
				handleTrigger.incrementAndGet();
			}

			@Override
			public void timeoutHandle(String requestID) {
				logger.info("Timeout {}", requestID);
				clientDone.incrementAndGet();
				timeoutTrigger.incrementAndGet();
			}
		});
		while (!client.isConnected()) {
			TimeUtils.waitSafeMili(1);
		}
		logger.info("Client started");
		int numReq = 20;
		for (int i = 0; i < numReq; i++) {
			Msg msg = new Msg(Unpooled.wrappedBuffer("hello".getBytes()), PayloadGenerator.testUrl, String.valueOf(i));
			client.send(msg);
		}
		while (clientDone.get() < numReq) {
			TimeUtils.waitSafeMili(1);
		}
		logger.info("Finish client {}", clientDone.get());
		while (serverDone.get() < numReq) {
			TimeUtils.waitSafeMili(1);
		}
		logger.info("Finish server {}", serverDone.get());
		TimeUtils.waitSafeMili(1000);
		logger.info("clientDone={}, timeoutTrigger={}, handleTrigger={}"
			, clientDone.get()
			, timeoutTrigger.get()
			, handleTrigger.get());
		logger.info("Timeout monitor {}", RequestTimeoutMonitor.report());
		assertEquals(clientDone.get(), numReq);
		assertEquals(timeoutTrigger.get(), numReq);
		assertEquals(handleTrigger.get(), 0);
	}
}
