package com.viettel.autotest.microchassis.feature.protoTest;

import com.viettel.autotest.microchassis.lib.SelfTestInstance;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author tiennn18
 */
public class HttpEchoTest extends SelfTestInstance {
	static HttpServer h1server; // default 1.1
	static HttpClient h1client; // default 1.1
	static final String testContent = "testr0n20r202u0x0pfncfyp0hfp0ym02cp0hfp0[0fyk2-90ypx3h0fh0x";

	static {
		try {
			h1server = new HttpServerOptions("h1server").newServer();
			h1client = new HttpClientOptions("h1client").newClientHttp1Upgrade();
			h1server.start(new HttpServerHandler(h1server.getConfig()) {
				@Override
				public void getHandle(HttpServerContext serverCtx) {
					try {
						System.out.println("echo server handle get");
						serverCtx.send(testContent.getBytes(StandardCharsets.UTF_8));
					} catch (Exception e) {
						logger.error(e, e);
					}
				}

				@Override
				public void postHandle(HttpServerContext serverCtx) {
					System.out.println("echo server handle post "+ serverCtx.getInData().length);
					try {
						serverCtx.send(serverCtx.getInData());
					} catch (Exception e) {
						logger.error(e, e);
					}
				}

				@Override
				public void putHandle(HttpServerContext serverCtx) {
					System.out.println("echo server handle put "+ serverCtx.getInData().length);
					try {
						serverCtx.send(serverCtx.getInData());
					} catch (Exception e) {
						logger.error(e, e);
					}
				}
			});
			h1client.start(new HttpClientHandler(h1client.getConfig()){
				public void handle(HttpClientReceiveContext ctx){
				byte[] incomData = ctx.getInData();
				//				System.out.println("Echoed: " + new String(incomData, StandardCharsets.UTF_8));
				//				assert Arrays.equals(incomData, content);
				System.out.println("Client received length: " + incomData.length + " " + ctx.getInMsg().toString(StandardCharsets.UTF_8));
				assert Arrays.equals(incomData, testContent.getBytes(StandardCharsets.UTF_8));
				isTested.set(true);
			}});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void doSend(HttpMethod method) {
		// connected
		try {
			h1client.send(method, "/tiennn18", content.get());
			System.out.println("Client sent length: " + content.get().length);
		} catch (Exception e) {
			System.out.println("Send error: " + e.getMessage());
			e.printStackTrace();
		}
		Assertions.assertDoesNotThrow(()-> TimeUtils.waitUntil(isTested::get, ChassisConfig.ConnectionConfig.DEFAULT_SEND_TIMEOUT_MS.get()));// timeout receive
	}

	@Test
	@DisplayName("Get testContent body")
	public void testGet() {
		isTested.set(false);
		content.set("wrong content-ypx3h0fh0x".getBytes(StandardCharsets.UTF_8));
		doSend(HttpMethod.GET);
	}

	@Test
	@DisplayName("Send and receive POST echo content body")
	public void testEchoPost() {
		isTested.set(false);
		content.set(testContent.getBytes(StandardCharsets.UTF_8));
		doSend(HttpMethod.POST);
	}

	@Test
	@DisplayName("Send and receive PUT echo content body")
	public void testEchoPut() {
		isTested.set(false);
		content.set(testContent.getBytes(StandardCharsets.UTF_8));
		doSend(HttpMethod.PUT);
	}
}
