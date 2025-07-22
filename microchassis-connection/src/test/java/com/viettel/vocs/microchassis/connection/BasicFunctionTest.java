package com.viettel.vocs.microchassis.connection;

import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConst;
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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BasicFunctionTest {
	protected static final Logger logger = LogManager.getLogger(BasicFunctionTest.class);

	private static final int port = 8088;
	private static HttpServer server;
	private static HttpClient client;
	private static final AtomicLong countAsyncReceive = new AtomicLong();
	public static final long numberOfRequest = 50;
	public static final long timeout = 2000;

	public BasicFunctionTest() {

	}

	private void setupServer() {
		try {
			HttpServerOptions options = new HttpServerOptions("test", port);
//			options.enable(ChassisConst.SupportVersion.HTTP_2_0);
			server = new HttpServer(options);
			server.start(new HttpServerHandler(server.getConfig()) {
				public void handle(HttpServerContext ctx) {
					try {
						HttpRequest inMsg = ctx.getInMsg();
						if (inMsg.readableBytes() > 0) {
							ctx.send(inMsg.toString(StandardCharsets.UTF_8));
						} else {
							ctx.send("Hi");
						}
					} catch (Exception e) {
						logger.error(e, e);
					}
				}
			});
			logger.info("===================SERVER FOR TEST STARTED===================");
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	private void setUpClient() {
		try {
			HttpClientOptions options = new HttpClientOptions("localhost", port, "test");

			client = options.newClientHttp2Only();
			client.start(new HttpClientHandler(options) {
				public void handle(HttpClientReceiveContext h2ctx) {
					logger.info("Receive async response " + countAsyncReceive.incrementAndGet() + "|Status: " + h2ctx.getInMsg().status() + "|head: " + h2ctx.getInMsg().headers());
				}
			});
			logger.info("===================CLIENT FOR TEST CONNECTED===================");
		} catch (Exception e) {
			logger.error(e, e);
		}

	}

	@BeforeAll
	public void setUpClass(){
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
			client.close();
			server.stop();
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	@BeforeEach
	public void setUp() {

	}

	@AfterEach
	public void tearDown() {

	}

	@Test
	void testAsyncRequest() {
		countAsyncReceive.set(0);
		AtomicLong id = new AtomicLong();
		try {
			for (int i = 0; i < numberOfRequest; i++) {
				long myid = id.incrementAndGet();
				String payload = "{\"requestId\":\"6000001\",\"msisdn\":\"6000001\",\"sessionType\":null,\"sessionName\":null,\"deviceId\":null,\"reqType\":\"mobile\",\"featureFlag\":null}";
				HttpRequest rq = client.createReq(POST, "/query", payload.getBytes());
				client.send(rq, myid + "");
				logger.info("Send request " + myid);
			}
			TimeUtils.waitSafeMili(numberOfRequest * 30);
		} catch (Exception e) {
			logger.error(e, e);
		}
		assertEquals(numberOfRequest, countAsyncReceive.get());
	}

	/**
	 * Test of onDisconnect method, of class HttpServerImpl.
	 */
	@Test
	void testSyncRequest() {
		AtomicLong id = new AtomicLong();
		int count = 0;

		try {
			for (int i = 0; i < numberOfRequest; i++) {
				long myid = id.incrementAndGet();
				String payload = "{\"requestId\":\"6000001\",\"msisdn\":\"6000001\",\"sessionType\":null,\"sessionName\":null,\"deviceId\":null,\"reqType\":\"mobile\",\"featureFlag\":null}";
				HttpRequest rq = client.createReq(POST, "/query", payload.getBytes());
				rq.headers().add(ChassisConst.CustomHeader.msgIdHeader, myid + "");
				HttpResponse sendSync = client.sendSync(rq);
				if (sendSync == null) {
					fail("Time out sync request");
				} else {
					if (sendSync.status().equals(HttpResponseStatus.OK)) {
						logger.info("Receive sync response|" + sendSync.headers());
						count++;
					} else {
						fail("Server reponse " + sendSync.status() + "|" + sendSync.toString(StandardCharsets.UTF_8));
					}
				}
			}
			assertEquals(numberOfRequest, count);
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	@Test
	void testEmptyGetRequest() {
		int count = 0;
		try {
			for (int i = 0; i < numberOfRequest; i++) {
				HttpRequest rq = client.createReq(GET, "/query");
				HttpResponse sendSync = client.sendSync(rq);
				if (sendSync == null) {
					fail("Time out sync request");
				} else {
					if (sendSync.status().equals(HttpResponseStatus.OK)) {
						count++;
					} else {
						fail("Server reponse " + sendSync.status());
					}
				}
			}
			assertEquals(numberOfRequest, count);
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	@Test
	void testPathVariableExtractor() {

		String userPattern1 = "/api/v1/tower/{towerID}/floor/{floorID}/room/{roomID}/{personID}";
		String userPattern2 = "/api/v1/tower/{towerID}/ab/floor/{floorID}/xyz/room/{roomID}/{personID}";
		String userPattern3 = "/api/v1/tower/{towerID}/{floorID}/{roomID}/{personID}";
		String userPattern4 = "/api/v1/room/{roomID}/persons";
		String userPattern5 = "/api/v1/persons/{personID}";
		String url1 = "/api/v1/tower/t1/floor/f1/room/r1/p1?query=name,abc,x";
		String url2 = "/api/v1/tower/t2/ab/floor/f2/xyz/room/r2/p2";
		String url3 = "/api/v1/tower/t3/f3/r3/p3";
		String url4 = "/api/v1/room/r4/persons";
		String url5 = "/api/v1/persons/p123445";
		String n_url1 = "/api/v1/tower/t1/floor/room/r1/p1?query=name,abc,x";
		String n_url2 = "/api/v1/tower/t1/floor/f1/f2/room/r1/p1?query=name,abc,x";
		try {
			logger.info(getPathVariable(userPattern1, url1));
			logger.info("===================================");
			logger.info(getPathVariable(userPattern2, url2));
			logger.info("===================================");
			logger.info(getPathVariable(userPattern3, url3));
			logger.info("===================================");
			logger.info(getPathVariable(userPattern4, url4));
			logger.info("===================================");
			logger.info(getPathVariable(userPattern5, url5));
			logger.info("===================================");
		} catch (Exception e) {
			logger.error(e);
			fail(e);
		}
		try {
			logger.info(getPathVariable(userPattern1, n_url1));
			logger.info("===================================");
		} catch (Exception e) {
			logger.error(e);
		}
		try {
			logger.info(getPathVariable(userPattern1, n_url2));
			logger.info("===================================");
		} catch (Exception e) {
			fail(e);
		}
	}

	public Map<String, String> getPathVariable(String userPattern, String url) throws Exception {
		Pattern p = Pattern.compile("\\{(\\w*)}");
		Matcher m = p.matcher(userPattern);
		Map<String, String> mapExtract = new HashMap<>();
		Map<String, Integer> subStringMap = new HashMap<>();
		while (m.find()) {
			String substring = userPattern.substring(0, m.start());
			int countMatches = StringUtils.countMatches(substring, "/");
			subStringMap.put(m.group(1), countMatches);
		}
		if (subStringMap.isEmpty()) {
			return null;
		}
		for (Map.Entry<String, Integer> entry : subStringMap.entrySet()) {
			Integer idx = entry.getValue();
			String name = "(/\\w*)({" + idx + "})";
			Pattern p2 = Pattern.compile(name);
			Matcher m2 = p2.matcher(url);
			if (m2.find()) {
				String x = m2.group(1);
				mapExtract.put(entry.getKey(), x.substring(1));
			} else {
				throw new Exception("Cannot find path variable for paramName: \"" + entry.getKey() + "\"");
			}
		}
		return mapExtract;
	}

	@Test
	void mixSyncAndAsyncRequest() {
		countAsyncReceive.set(0);
		AtomicLong countSyncReceive = new AtomicLong();
		ExecutorService threadSync = Executors.newSingleThreadExecutor();
		ExecutorService threadAsync = Executors.newSingleThreadExecutor();
		String syncPayload = "Sync payload ";
		String asyncPayload = "ASync payload ";
		threadSync.execute(() -> {
			for (int i = 0; i < numberOfRequest; i++) {
				String myid = "sync-" + i;
				HttpRequest rq = client.createReq(POST, PayloadGenerator.testUrl, syncPayload.getBytes());
				rq.headers().set("id", myid);
				try {
					HttpResponse sendSync = client.sendSync(rq);
					if (sendSync == null) {
						fail("Time out sync");
					} else {
						String toString = sendSync.toString(StandardCharsets.UTF_8);
						logger.info("Receive sync response|Headers " + sendSync.headers() + "|Content :" + toString);
						assertEquals(syncPayload, toString);
						countSyncReceive.incrementAndGet();
					}
				} catch (Exception e) {
					logger.error(e, e);
					fail(e);
				}
			}
		});

		threadAsync.execute(() -> {
			for (int i = 0; i < numberOfRequest; i++) {
				HttpRequest rq = client.createReq(POST, PayloadGenerator.testUrl, asyncPayload.getBytes());
				String myid = "async-" + i;
				try {
					client.send(rq, myid);
				} catch (Exception e) {
					logger.error(e, e);
					fail(e);
				}
			}
		});
		long t = System.currentTimeMillis();
		while (countAsyncReceive.get() != numberOfRequest || countSyncReceive.get() != numberOfRequest) {
			LockSupport.parkNanos(1000);
			if (System.currentTimeMillis() - t > (numberOfRequest * timeout) * 2) {
				fail("Timeout");
				break;
			}
		}
	}

	@Test
	void mixSyncAndAsync() {
		countAsyncReceive.set(0);
		int countAsync = 0;
		int countSync = 0;
		String syncPayload = "Sync payload ";
		String asyncPayload = "ASync payload ";
		for (int i = 0; i < numberOfRequest; i++) {

			double random = Math.random();
			try {
				if (random > 0.5) {
					HttpRequest rq = client.createReq(POST, PayloadGenerator.testUrl, syncPayload.getBytes());
					String myid = "sync-" + i;
					rq.headers().set("id", myid);
					HttpResponse msg = client.sendSync(rq);
					if (msg == null) {
						fail("Time out sync");
					} else {
						String toString = msg.toString(StandardCharsets.UTF_8);
						logger.info("Receive sync response|Headers " + msg.headers() + "|Content :" + toString);
						assertEquals(syncPayload, toString);
						countSync++;
					}
				} else {
					HttpRequest rq = client.createReq(POST, PayloadGenerator.testUrl, asyncPayload.getBytes());
					String myid = "async-" + i;
					rq.headers().set("id", myid);
					client.send(rq, myid);
					countAsync++;
				}
			} catch (Exception e) {
				logger.error(e, e);
				fail(e);
			}
		}
		logger.info("Finish send " + numberOfRequest + " request");
		long t = System.currentTimeMillis();
		while (countAsyncReceive.get() != countAsync) {
			LockSupport.parkNanos(1000);
			if (System.currentTimeMillis() - t > (numberOfRequest * timeout)) {
				fail("Timeout");
				break;
			}
		}
		assertEquals(numberOfRequest, countAsyncReceive.get() + countSync);
	}

	@Test
	void testSyncRequestMultiThread() {
		AtomicLong id = new AtomicLong();
		int numThread = 2;
		ExecutorService service = Executors.newFixedThreadPool(numThread);
		List<AtomicLong> lSuccess = new ArrayList<>();
		for (int i = 0; i < numThread; i++) {
			lSuccess.add(new AtomicLong());
		}

		for (int j = 0; j < numThread; j++) {
			int k = j;
			service.execute(() -> {
				for (int i = 0; i < numberOfRequest; i++) {
					String myid = id.incrementAndGet() + "-" + "thread" + k;
					String payload = "mypayload-thread-" + k;
					HttpRequest rq = client.createReq(GET, "/api/v1/health", payload.getBytes());
					rq.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), HttpScheme.HTTP.toString());
					rq.setMessageId(myid);
					try {
						HttpResponse sendSync = client.sendSync(rq);
						if (sendSync.status().equals(HttpResponseStatus.OK)) {
							logger.info("Receive sync response|" + sendSync.headers());
							String get = sendSync.getMessageId();
							assertTrue(get != null && !get.isEmpty());
							assertEquals(myid, get);
							assertEquals(payload, sendSync.toString(StandardCharsets.UTF_8));
							lSuccess.get(k).incrementAndGet();
						} else {
							fail("Server reponse " + sendSync.status() + "|" + sendSync.toString(StandardCharsets.UTF_8));
						}
					} catch (Exception e) {
						logger.error(e, e);
						fail(e);
					}
				}
				logger.info("Thread " + k + " finish");
			});
		}
		long start = System.currentTimeMillis();
		while (true) {
			try {
				long success = lSuccess.stream().mapToLong(AtomicLong::get).sum();
				if (success == numThread * numberOfRequest) {
					assertEquals(numThread * numberOfRequest, success);
					logger.info("Sent: " + numThread * numberOfRequest + ". Success " + success);
					break;
				}
				if (System.currentTimeMillis() - start > numberOfRequest * numThread * 30) {
					fail("Time out");
					return;
				}
				TimeUtils.waitSafeMili(1);
			} catch (Exception e) {
				logger.error(e, e);
			}
		}
	}

	@Test
	void testAsyncSyncRequestMultiThread() {
		AtomicLong id = new AtomicLong();
		AtomicLong countAsyncSent = new AtomicLong();
		int numThread = 2;
		ExecutorService service = Executors.newFixedThreadPool(numThread + 1);
		List<AtomicLong> lSuccess = new ArrayList<>();
		for (int i = 0; i < numThread; i++) {
			lSuccess.add(new AtomicLong());
		}

		for (int j = 0; j < numThread; j++) {
			int k = j;
			service.execute(() -> {
				for (int i = 0; i < numberOfRequest; i++) {
					String myid = id.incrementAndGet() + "-" + "thread" + k;
					String payload = "mypayload-thread-" + k;
					HttpRequest rq = client.createReq(GET, "/api/v1/health", payload.getBytes());
					rq.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), HttpScheme.HTTP.toString());
					rq.headers().add(ChassisConst.CustomHeader.msgIdHeader, myid);
					try {
						HttpResponse sendSync = client.sendSync(rq);
						if (sendSync.status().equals(HttpResponseStatus.OK)) {
							logger.info("Receive sync response|" + sendSync.headers());
							String get = sendSync.getMessageId();
							assertTrue(get != null && !get.isEmpty());
							assertEquals(myid, get);
							assertEquals(payload, sendSync.toString(StandardCharsets.UTF_8));
							lSuccess.get(k).incrementAndGet();
						} else {
							fail("Server reponse " + sendSync.status() + "|" + sendSync.toString(StandardCharsets.UTF_8));
						}
					} catch (Exception e) {
						logger.error(e, e);
						fail(e);
					}
				}
				logger.info("Thread " + k + " finish");
			});
		}
		countAsyncReceive.set(0);
		service.execute(() -> {
			try {
				for (int i = 0; i < numberOfRequest; i++) {
					String payload = "myAsync-thread-" + Thread.currentThread().getName();
					HttpRequest rq = client.createReq(POST, PayloadGenerator.testUrl, payload.getBytes());
					String myid = "async-" + countAsyncSent.incrementAndGet();
					rq.headers().set("id", myid);
					client.send(rq, myid);
				}
			} catch (Exception e) {
				fail(e);
			}
		});
		long start = System.currentTimeMillis();
		while (true) {
			try {
				long success = lSuccess.stream().mapToLong(AtomicLong::get).sum() + countAsyncReceive.get();
				if (success == (numThread + 1) * numberOfRequest) {
					assertEquals((numThread + 1) * numberOfRequest, success);
					logger.info("Sent: " + (numThread + 1) * numberOfRequest + ". Success " + success);
					break;
				}
				if (System.currentTimeMillis() - start > numberOfRequest * numThread * 3000) {
					fail("Time out");
					return;
				}
				TimeUtils.waitSafeMili(1);
			} catch (Exception e) {
				logger.error(e, e);
			}
		}
	}

}
