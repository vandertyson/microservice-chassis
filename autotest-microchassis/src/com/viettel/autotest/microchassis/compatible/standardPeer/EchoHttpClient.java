package com.viettel.autotest.microchassis.compatible.standardPeer;

import com.viettel.autotest.microchassis.ChassisAutotestConfig;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.ocs.test.RequestFakeJson;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.handler.codec.http.HttpMethod.POST;

/**
 * @author tiennn18
 */
public class EchoHttpClient {
	static boolean devMode = true;
	static final AtomicInteger successCounter = new AtomicInteger();
	HttpClient client;
	@AfterEach
	public void cleanup() {
		client.close();
	}

	private static void sendSync(HttpClient client, int i) {
		final String data = new RequestFakeJson().toString();
		byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
		HttpRequest syncReq = client.createReq(POST, "/sync", dataBytes);// Replace with your endpoint
//		syncReq.headers()
//			.set("Content-Type", "application/json"); // Set headers
//			.set("Content-Length", String.valueOf(dataBytes.length));
		try {
			HttpResponse syncRes = client.sendSync(syncReq);
			try {
				System.out.println("Received response with status code: " + syncRes.status() + ". #" + i);
				if (syncRes.toStringUTF8().equals(data)) successCounter.incrementAndGet();
				else
					Assertions.fail(String.format("Not match content %s | %s", syncRes.toStringUTF8(), data));
			} catch (Throwable throwable) {
				throwable.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static Logger logger = LogManager.getLogger(MyServer.class);

	public void setupHttp2Only() throws Exception {
		client = new HttpClientOptions("localhost", ChassisAutotestConfig.thirdPartyPort, "test")
			.newClientHttp2Only();
		clientStart();
	}

	public void setupHttp2Fallback() throws Exception {
		client = new HttpClientOptions("localhost", ChassisAutotestConfig.thirdPartyPort, "test")
			.newClientHttp2Fallback();
		clientStart();
	}

	public void setupHttp1Upgrade() throws Exception {
		client = new HttpClientOptions("localhost", ChassisAutotestConfig.thirdPartyPort, "test")
			.newClientHttp1Upgrade();
		clientStart();
	}

	public void setupHttp1Only() throws Exception {
		client = new HttpClientOptions("localhost", ChassisAutotestConfig.thirdPartyPort, "test")
			.newClientHttp1Only();
		clientStart();
	}
	private void clientStart() throws TimeoutException {
		client.start(new HttpClientHandler(client.getConfig()) {
			public void handle(HttpClientReceiveContext hctx) {
				if (hctx.getInMsg().status().equals(HttpResponseStatus.OK) && hctx.getInData().length > 0)
					logger.info("Receive async response " + successCounter.incrementAndGet() + "|Status: " + hctx.getInMsg().status() + "|head: " + hctx.getInMsg().headers());
			}
		});
	}
	@BeforeAll
	public static void setup() {
		LogUtils.setupLog4j2();
	}
	@BeforeEach
	public void resetTest(){
		successCounter.set(0);
	}

	@Test
	public void chassisClientHttp1OnlyTest() throws Exception {
		setupHttp1Only();
		for (int i = 0; i < 1; i++) sendSync(client, i);
		Assertions.assertTrue(TimeUtils.completeUnder(() -> successCounter.get() == 1, devMode ? Integer.MAX_VALUE : 3000));
	}
	@Test
	public void chassisClientHttp1UpgradeTest() throws Exception {
		setupHttp1Upgrade();
		for (int i = 0; i < 1; i++) sendSync(client, i);
		Assertions.assertTrue(TimeUtils.completeUnder(() -> successCounter.get() == 1, devMode ? Integer.MAX_VALUE : 3000));
	}

	@Test
	public void chassisClientHttp2OnlyTest() throws Exception {
		setupHttp2Only();
		for (int i = 0; i < 1; i++) sendSync(client, i);
		Assertions.assertTrue(TimeUtils.completeUnder(() -> successCounter.get() == 1, devMode ? Integer.MAX_VALUE : 3000));
	}
	@Test
	public void chassisClientHttp2FallbackTest() throws Exception {
		setupHttp2Fallback();
		for (int i = 0; i < 1; i++) sendSync(client, i);
		Assertions.assertTrue(TimeUtils.completeUnder(() -> successCounter.get() == 1, devMode ? Integer.MAX_VALUE : 3000));
	}
}
