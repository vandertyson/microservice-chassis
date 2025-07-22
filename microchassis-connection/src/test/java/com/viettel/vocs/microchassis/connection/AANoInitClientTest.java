package com.viettel.vocs.microchassis.connection;

import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AANoInitClientTest {
	protected static final Logger logger = LogManager.getLogger(AANoInitClientTest.class);

	public static final int port = 7777;
	private static HttpServer server;

	@BeforeAll
	public void setUpClass(){
		LogUtils.setupLog4j2();
		try {
			logger.info("Setting up");
			setupServer();
			TimeUtils.waitSafeMili(100);
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	@AfterAll
	public void tearDownClass() {
		try {
			server.stop();
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	private void setupServer() {
		try {
			HttpServerOptions options = new HttpServerOptions("test", port);
			server = options.newServer();
			server.start(new HttpServerHandler(options){
				@Override
				public void handle(HttpServerContext hctx) {
					try {
						hctx.send(hctx.getInMsg().readableBytes() > 0
							? hctx.getInMsg().toString(StandardCharsets.UTF_8)
							: "Hi");
					} catch (Exception e) {
						logger.error(e, e);
					}
			}});
			logger.info("===================SERVER FOR TEST STARTED===================");
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	@Test
	void testSendSync() throws Exception {
		HttpClientOptions opt = new HttpClientOptions("127.0.0.1", port, "test");
//		opt.enable(ChassisConst.SupportVersion.HTTP_2_0);
		HttpClient client = opt.newClientHttp2Only();
		try {
			client.start(new HttpClientHandler(opt));
			String payload = "ok sent";
			HttpRequest request = client.createReq(HttpMethod.GET, "/hello", payload.getBytes(StandardCharsets.UTF_8));

			HttpResponse response = client.sendSync(request);
			if (response == null) {
				fail("no response");
			}
			assertEquals(HttpResponseStatus.OK, response.status());
			assertEquals(payload, response.toString(StandardCharsets.UTF_8));
			response.decompose();
		} catch (Exception ex) {
			fail(ex);
		} finally {
			client.close();
		}
	}
}
