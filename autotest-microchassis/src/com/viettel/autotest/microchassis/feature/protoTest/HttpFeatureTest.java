package com.viettel.autotest.microchassis.feature.protoTest;

import com.viettel.autotest.microchassis.benchmark.peer.ClientBenchmark;
import com.viettel.autotest.microchassis.lib.generator.PayloadGenerator;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.netty.handler.codec.http.HttpMethod.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class HttpFeatureTest extends ClientBenchmark {
	protected static final Logger logger = LogManager.getLogger(Http1FeatureTest.class);
	protected static HttpServer server;
	ExecutorService threadSync = Executors.newSingleThreadExecutor();
	ExecutorService threadAsync = Executors.newSingleThreadExecutor();

	protected boolean sendSync(int i) {
		logger.info("Iterrate sync " + i);
		HttpRequest rq = makeRequest(POST);
		logger.info("Presend sync " + i + " " + rq.getMessageId());
		try {
			HttpResponse httpResponse = getClient().sendSync(rq);
			countSyncSend.incrementAndGet();
			validateSyncResponse(httpResponse);
			if(httpResponse!=null) httpResponse.decompose();
			return true;
		} catch (Exception e) {
			logger.error("Send sync error " + i, e);
			fail(e);
			return false;
		}
	}

	protected boolean sendAsync(long i) {
		logger.info("Iterrate async " + i);
		HttpRequest rq = makeRequest(POST);
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

	protected void setupServer() {
		try {
			HttpServerOptions options = new HttpServerOptions("test", port);
			options.setAutoDecompose(false);
			server = new HttpServer(options);
			server.start(makeHSHandler.apply(options));
			logger.info("===================SERVER FOR TEST STARTED===================");
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	protected HttpClient getClient() {
		return (HttpClient) client;
	}

	public Function<HttpClientOptions, HttpClientHandler> clientHandler = config -> new HttpClientHandler(config) {
		public void handle(HttpClientReceiveContext ctx) {
			HttpResponse inMsg = ctx.getInMsg();
			validateAsyncResponse(ctx);
			logger.info("Receive async response " + countAsyncReceive.get() + "|Status: " + inMsg.status() + "|head: " + inMsg.headers() + "|msg: " + inMsg.toString(StandardCharsets.UTF_8));
			if(!config.decomposeAfterHandle) ctx.decompose();
		}

		@Override
		public void dropHandle(HttpRequest failedReq, boolean isSent) {
			super.dropHandle(failedReq, isSent);
		}

		@Override
		public void timeoutHandle(String requestID) {
			super.timeoutHandle(requestID);
		}
	};

	protected void validateAsyncResponse(HttpClientReceiveContext ctx) {
		HttpResponse inMsg = ctx.getInMsg();
		if (inMsg == null) fail("Receive response null");
		if(!HttpResponseStatus.OK.equals(inMsg.status())) fail("Not OK");
		String stringUTF8 = inMsg.toStringUTF8();
		String rcvId = inMsg.getMessageId();
		if(client.getConfig().to3rdParty || stringUTF8.contains(rcvId)) countAsyncReceive.incrementAndGet();
		else fail("msgId not match " + rcvId +" vs "+ stringUTF8);
	}
	protected void validateSyncResponse(HttpResponse inMsg) {
		if (inMsg == null) fail("Time out sync request");
		if(!HttpResponseStatus.OK.equals(inMsg.status())) fail("Not OK");
		String stringUTF8 = inMsg.toStringUTF8();
		String rcvId = inMsg.getMessageId();
		if(client.getConfig().to3rdParty || stringUTF8.contains(rcvId)) countSyncReceive.incrementAndGet();
		else fail("msgId not match " + rcvId +" vs "+ stringUTF8);
	}

	protected Function<HttpServerOptions, HttpServerHandler> makeHSHandler = options -> new HttpServerHandler(options) {
		@Override
		public void handle(HttpServerContext ctx) {
			try {
				byte[] payload = ctx.getInData();
				logger.info("received msg th "+serverRcv.incrementAndGet() + " msgId " + ctx.getInID() + " data " + ctx.getInMsg().toStringUTF8());
				ctx.getCoMsg().headers().set(ctx.getInMsg().headers()); // echo headers
				if (payload.length > 0) {
					ctx.send(payload);
				} else {
					ctx.send("Hi");
				}
			} catch (Exception e) {
				logger.error(e, e);
			} finally {
				if(!config.decomposeAfterHandle) ctx.decompose();
			}
		}
	};

	@BeforeAll
	public void setUpClass() {
		LogUtils.setupLog4j2();
		try {
			logger.info("Setting up");
			setupServer();
			TimeUtils.waitSafeMili(100);
			setUpClient();
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	@AfterAll
	public void tearDownClass() {
		try {
			logger.info("tearDownClass");
			client.close();
			server.stop();
		} catch (Exception e) {
			logger.error(e, e);
		}
	}


	@AfterEach
	public void tearDown() {

	}

	protected HttpRequest makeRequest(HttpMethod method) {
		HttpRequest req = getClient().createReq(method, PayloadGenerator.testUrl);
		req.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), HttpScheme.HTTP);
		String payload = "{\"requestId\":\""+req.getMessageId()+"\",\"msisdn\":\"6000001\",\"sessionType\":null,\"sessionName\":null,\"deviceId\":null,\"reqType\":\"mobile\",\"featureFlag\":null}";
		req.writeFrom(payload.getBytes(StandardCharsets.UTF_8));
		return req;
	}
	@Override
	protected HttpRequest makeRequest(ByteBufAllocator allocator) {
		return makeRequest(POST);
	}

	// TEST

	@Test
	@Order(1)
	void testAsyncRequest() {
		try {
			for (int i = 0; i < numberOfRequest; i++) {
				long myid = id.incrementAndGet();
				HttpRequest req = makeRequest(POST);
				client.send(req);
				logger.info("Send request " + myid + " msgId " + req.getMessageId());
			}
			TimeUtils.waitUntil(() -> numberOfRequest == countAsyncReceive.get(), timeout + numberOfRequest * 30);
		} catch (TimeoutException e) {
			fail(String.format("Timeout as%d/%d", countAsyncReceive.get(), numberOfRequest));
		} catch (Exception e) {
			fail("Send failed " +e.getMessage());
		}
	}

	/**
	 * Test of onDisconnect method, of class HttpServerImpl.
	 */
	@Test
	@Order(2)
	void testSyncRequest() {
		try {
			for (int i = 0; i < numberOfRequest; i++) {
				while (!getClient().isSendSyncable()) TimeUtils.waitSafeMili(1);
				HttpRequest req = makeRequest(HttpMethod.POST);
				HttpResponse sendSync = getClient().sendSync(req);
				validateSyncResponse(sendSync);
				if(sendSync!= null) sendSync.decompose();
			}
			assert countSyncReceive.get() > 0;
			assertEquals(numberOfRequest, countSyncReceive.get());
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	@Test
	@Order(4)
	void mixSyncAndAsyncRequest() {
		threadSync.execute(() -> {
			for (int i = 0; i < numberOfRequest; i++) sendSync(i);
		});
		threadAsync.execute(() -> {
			for (long i = numberOfRequest; i < numberOfRequest + numberOfRequest; i++) sendAsync(i);
		});
		try {
			TimeUtils.waitUntil(() -> countAsyncSend.get() == numberOfRequest
				&& countAsyncReceive.get() == numberOfRequest
				&& countSyncReceive.get() == numberOfRequest, 20000);
		} catch (TimeoutException e) {
			fail(String.format("Timeout as%d/%d s%d/%d", countAsyncReceive.get(), countAsyncSend.get(), countSyncReceive.get(), countSyncSend.get()));
		}
	}

	@Test
	@Order(5)
	void mixSyncAndAsync() {
		for (int i = 0; i < numberOfRequest; i++) {
			if (Math.random() > 0.5) sendSync(i);
			else sendAsync(i);
		}
		logger.info("Finish send " + numberOfRequest + " request");
		try {
			TimeUtils.waitUntil(() -> countAsyncReceive.get() == countAsyncSend.get()
					&& countSyncReceive.get() == countSyncSend.get()
					&& numberOfRequest == countAsyncSend.get() + countSyncSend.get()
				, numberOfRequest*10 + timeout);
		} catch (TimeoutException e) {
			fail(String.format("Timeout as%d/%d s%d/%d", countAsyncReceive.get(), countAsyncSend.get(), countSyncReceive.get(), countSyncSend.get()));
		}
	}

	@Test
	@Order(6)
	void testSyncRequestMultiThread() {
		int numThread = 2;
		ExecutorService service = Executors.newFixedThreadPool(numThread);
		List<AtomicLong> lSuccess = IntStream.range(0, numThread).mapToObj(i -> new AtomicLong()).collect(Collectors.toList());

		for (int j = 0; j < numThread; j++) {
			int finalJ = j;
			service.execute(() -> {
				for (int i = 0; i < numberOfRequest; i++) if (sendSync(i)) lSuccess.get(finalJ).incrementAndGet();
				logger.info("Thread " + finalJ + " finish");
			});
		}
		try {
			TimeUtils.waitUntil(() ->
					lSuccess.stream().mapToLong(AtomicLong::get).sum() == countSyncSend.get()
						&& countSyncSend.get() == countSyncReceive.get()
						&& numberOfRequest * numThread == countSyncReceive.get() + countAsyncReceive.get()
				, numberOfRequest * 10 + numThread * 3000);
		} catch (TimeoutException e) {
			if(countSyncReceive.get() != countSyncSend.get())
				fail(String.format("Timeout s%d/%d", countSyncReceive.get(), countSyncSend.get()));
		}
	}

	@Test
	@Order(7)
	void testAsyncSyncRequestMultiThread() {
		int numThread = 2;
		ExecutorService service = Executors.newFixedThreadPool(numThread + 1);
		List<AtomicLong> lSuccess = IntStream.range(0, numThread).mapToObj(i -> new AtomicLong()).collect(Collectors.toList());

		for (int j = 0; j < numThread; j++) {
			int finalJ = j;
			service.execute(() -> {
				for (int i = 0; i < numberOfRequest; i++) if (sendSync(i)) lSuccess.get(finalJ).incrementAndGet();
				logger.info("Thread " + finalJ + " finish");
			});
		}
		service.execute(() -> {
			for (int i = 0; i < numberOfRequest; i++) sendAsync(i);
		});
		try {
			TimeUtils.waitUntil(() ->
					lSuccess.stream().mapToLong(AtomicLong::get).sum() == countSyncSend.get()
						&& countSyncSend.get() == countSyncReceive.get()
						&& numberOfRequest * (numThread + 1) == countAsyncReceive.get() + countSyncReceive.get()
						&& countAsyncReceive.get() == countAsyncSend.get(),
				numberOfRequest * 10 + numThread * 3000);
		} catch (TimeoutException e) {
			fail(String.format("Timeout as%d/%d s%d/%d", countAsyncReceive.get(), countAsyncSend.get(), countSyncReceive.get(), countSyncSend.get()));
		}
	}
}





