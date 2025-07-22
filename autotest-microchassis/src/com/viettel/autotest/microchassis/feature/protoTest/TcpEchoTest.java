package com.viettel.autotest.microchassis.feature.protoTest;

import com.viettel.autotest.microchassis.lib.SelfTestInstance;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.codec.handler.tcp.EchoTcpHandler;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.tcp.client.TcpClient;
import com.viettel.vocs.microchassis.tcp.server.TcpServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

public class TcpEchoTest extends SelfTestInstance {
	static TcpServer server;
	static EchoTcpHandler echoTcpHandler;
	static TcpClient client;

	static {
		LogUtils.setupLog4j2();
		try {
			server = new TcpServer(new ServerConfiguration("tcpServer", 12345));
			echoTcpHandler = new EchoTcpHandler(server.getConfig());
			ClientConfiguration config = new ClientConfiguration("localhost", 12345, "tcpTest")
				.setTo3rdParty(false);
//			config.mapDnsIp.put("localhost", List.of("1.1.1.1", "1.1.1.2", "1.1.1.3", "1.1.1.4", "1.1.1.5", "1.1.1.6", "1.1.1.7", "1.1.1.8", "1.1.1.9", "1.1.1.10", "1.1.1.11", "1.1.1.12", "1.1.1.13", "1.1.1.14", "1.1.1.15"));
			config.startWaitStable = false;
			client = new TcpClient(config);
			server.start(echoTcpHandler);
			client.start(new TcpHandler(client.getConfig()) {
				@Override
				public void handle(TcpContext ctx) {
					byte[] incomData = ctx.getInData();
					//				System.out.println("Echoed: " + new String(incomData, StandardCharsets.UTF_8));
					//				assert Arrays.equals(incomData, content);
					System.out.println("Client received length: " + incomData.length);
					assert Arrays.equals(incomData, content.get());
					isTested.set(true);
				}
			});
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void doSend() {
		// connected
		try {
			client.send(content.get(), "/tiennn18", "aloalo1234");
			System.out.println("Client sent length: " + content.get().length);
		} catch (Exception e) {
			System.out.println("Send error: " + e.getMessage());
		}
		Assertions.assertDoesNotThrow(() -> {
			try {
				TimeUtils.waitUntil(isTested::get, ChassisConfig.ConnectionConfig.DEFAULT_SEND_TIMEOUT_MS.get());
			} catch (TimeoutException e) {
				e.printStackTrace();
			}
		});// timeout receive
	}

	private void doPush(boolean broadcast) {
		// connected
		try {
			server.send(server.createReq(content.get(), "/tiennn18", "aloalo1234"), broadcast);
			System.out.println("Server sent length: " + content.get().length);
		} catch (Exception e) {
			System.out.println("Send error: " + e.getMessage());
		}
		Assertions.assertDoesNotThrow(() -> TimeUtils.waitUntil(isTested::get, ChassisConfig.ConnectionConfig.DEFAULT_SEND_TIMEOUT_MS.get()));// timeout receive
	}

	@Test
	@DisplayName("Send and receive empty body")
	public void testEchoEmpty() {
		isTested.set(false);
		content.set(new byte[0]);
		doSend();
	}

	@Test
	@DisplayName("Server push broadcast")
	public void testServerPushBroadcast() {
		isTested.set(false);
		content.set("testr0n20r202u0x0pfncfyp0hfp0ym02cp0hfp0[0fyk2-90ypx3h0fh0x".getBytes(StandardCharsets.UTF_8));
		doPush(true);
	}

	@Test
	@DisplayName("Server push 1")
	public void testServerPush1() {
		isTested.set(false);
		content.set("testr0n20r202u0x0pfncfyp0hfp0ym02cp0hfp0[0fyk2-90ypx3h0fh0x".getBytes(StandardCharsets.UTF_8));
		doPush(false);
	}

	@Test
	@DisplayName("Send and receive content body")
	public void testEchoBody() throws Exception {
		isTested.set(false);
		content.set("testr0n20r202u0x0pfncfyp0hfp0ym02cp0hfp0[0fyk2-90ypx3h0fh0x".getBytes(StandardCharsets.UTF_8));
		doSend();
	}
}
