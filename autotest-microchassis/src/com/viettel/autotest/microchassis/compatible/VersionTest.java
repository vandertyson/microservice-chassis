package com.viettel.autotest.microchassis.compatible;

import com.viettel.autotest.microchassis.lib.generator.PayloadGenerator;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static io.netty.handler.codec.http.HttpMethod.POST;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author tiennn18
 */
public class VersionTest {
	protected static final Logger logger = LogManager.getLogger(VersionTest.class);

	@BeforeAll
	public void setUpClass(){
		LogUtils.setupLog4j2();
	}

	final String syncPayload = "Sync payload ";
	final String asyncPayload = "ASync payload ";
	public static final long timeout = 2000;
	private static final AtomicLong countAsyncReceive = new AtomicLong(0);
	private static final AtomicLong countSyncReceive = new AtomicLong(0);
	private static final AtomicLong countSyncSend = new AtomicLong(0);
	private static final AtomicLong countAsyncSend = new AtomicLong(0);
	private static HttpClient client;
	private HttpRequest prepareMsg(String messageId, String content){
		HttpRequest rq = client.createReq(POST, PayloadGenerator.testUrl, content.getBytes());
		rq.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), HttpScheme.HTTP);
		if(messageId!=null) rq.setMessageId(messageId);
		return rq;
	}
	private boolean sendSync(int i, String messageId, String content) {
		logger.info("Iterrate sync " + i);
		HttpRequest rq = prepareMsg(messageId, content);
		String sendId = rq.getMessageId();
		logger.info("Presend sync " + i + " " + rq.getMessageId());
		try {
			HttpResponse sendSync = client.sendSync(rq);
			countSyncSend.incrementAndGet();
			if (sendSync == null) {
				fail("Time out sync");
				return false;
			} else {
				if (sendSync.status().equals(HttpResponseStatus.OK)) {
					String get = sendSync.getMessageId();
					assertTrue(get != null && !get.isEmpty());
					assertEquals(messageId == null ? sendId : messageId, get);
					String toString = sendSync.toString(StandardCharsets.UTF_8);
					logger.info("Receive sync response|Headers " + sendSync.headers() + "|Content :" + toString);
					assertEquals(content, toString);
					countSyncReceive.incrementAndGet();
					sendSync.decompose();
					return true;
				} else {
					fail("Server reponse " + sendSync.status() + "|" + sendSync.toString(StandardCharsets.UTF_8));
					return false;
				}
			}
		} catch (Exception e) {
			logger.error("Send sync error {}", i);
			logger.error(e, e);
			fail(e);
			return false;
		}
	}

	private boolean sendAsync(long i, String messageId, String content) {
		logger.info("Iterrate async " + i);
		HttpRequest rq = prepareMsg(messageId, content);
		String sendId = rq.getMessageId();
		logger.info("Presend async " + i + " " + rq.getMessageId());
		try {
			client.send(rq);
			logger.info("Sent async {} {}", i, sendId);
			countAsyncSend.incrementAndGet();
		} catch (Exception e) {
			logger.error("Send async error {}", i);
			logger.error(e, e);
			fail(e);
			return false;
		}
		return true;
	}
	private void setUpClient() {
		try {
			HttpClientOptions config = new HttpClientOptions("localhost", 9021, "test");
//			config.setAutoDecompose(false);
			(client = config.newClientHttp1Upgrade()).start(new HttpClientHandler(config) {
				public void handle(HttpClientReceiveContext ctx) {
					HttpResponse inMsg = ctx.getInMsg();
					logger.info("Receive async response " + countAsyncReceive.incrementAndGet() + "|Status: " + inMsg.status() + "|head: " + inMsg.headers() + "|msg: " + inMsg.toString(StandardCharsets.UTF_8));
				}
			});
			logger.info("===================CLIENT FOR TEST CONNECTED===================");
		} catch (Exception e) {
			logger.error(e, e);
		}

	}
//	@BeforeAll
//	public void setUpClass() {
//		try {
//			logger.info("Setting up");
//			setUpClient();
//		} catch (Exception e) {
//			logger.error(e, e);
//		}
//	}
//
//	@AfterAll
//	public void tearDownClass() {
//		try {
//			client.close();
//		} catch (Exception e) {
//			logger.error(e, e);
//		}
//	}
	@Test
	void mixSyncAndAsync() {
		countAsyncReceive.set(0);
		countSyncReceive.set(0);
		countAsyncSend.set(0);
		countSyncSend.set(0);
		int numberOfRequest = 50;
		for (int i = 0; i < numberOfRequest; i++) {
			if (Math.random() > 0.5) sendSync(i, null,asyncPayload + i);
			else sendAsync(i, null, asyncPayload + i);
		}
		logger.info("Finish send " + numberOfRequest + " request");
		long t = System.currentTimeMillis();
		while (countAsyncReceive.get() != countAsyncSend.get()
			|| countSyncReceive.get() != countSyncSend.get()
			|| countSyncReceive.get() + countAsyncReceive.get() != numberOfRequest) {
			TimeUtils.waitSafeMili(1000);
			if (System.currentTimeMillis() - t > (numberOfRequest * timeout)) {
				fail("Timeout");
				break;
			}
		}
	}
	@Test
	void hServer() throws Exception {
		try {
			HttpServer server = new HttpServer(new HttpServerOptions("4.2.0", 9020));
			server.start(new HttpServerHandler(server.getConfig()));
			TimeUtils.waitSafeMili(1000000000);
		} catch (Throwable ex) {
			logger.error(ex.getMessage(), ex);
			throw ex;
		}
	}
}
