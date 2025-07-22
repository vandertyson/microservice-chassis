package com.viettel.autotest.microchassis.compatible.standardPeer;

import com.viettel.autotest.microchassis.lib.SelfTestInstance;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.tcp.EchoTcpHandler;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.tcp.client.TcpClient;
import com.viettel.vocs.microchassis.tcp.client.TcpInstanceClient;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import com.viettel.vocs.microchassis.tcp.server.TcpServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

/**
 * @author tiennn18
 */
public class TcpCompabilityTest extends SelfTestInstance {

	static Handler<TcpContext> asyncSuccessHandle = tctx -> {
		byte[] incomData = tctx.getInData();
		//				System.out.println("Echoed: " + new String(incomData, StandardCharsets.UTF_8));
		//				assert Arrays.equals(incomData, content);
		System.out.println("Client received length: " + incomData.length);
		assert Arrays.equals(incomData, content.get());
		isTested.set(true);
	};
	static TcpClient client;
	static TcpServer server;
	static TcpInstanceClient iClient;

	static {
		try {
			client = new TcpClient(new ClientConfiguration("localhost", 6367, "tcpTest"));
			server = new TcpServer(new ServerConfiguration("test-server", 6366));
			iClient = TcpInstanceClient.createInstance("localhost", asyncSuccessHandle, timeoutId -> System.out.println("Timed out msg " + timeoutId));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void doSend() {
		// connected
		try {
			Msg request = new Msg(iClient.getBytebufAllocator(), "/tiennn18", "aloalo1234");
			request.writeFrom(content.get());
			iClient.send("localhost", 6367, request);
			System.out.println("Client sent length: " + content.get().length);
		} catch (Exception e) {
			System.out.println("Send error: " + e.getMessage());
		}
		Assertions.assertDoesNotThrow(() -> TimeUtils.waitUntil(isTested::get, ChassisConfig.ConnectionConfig.DEFAULT_SEND_TIMEOUT_MS.get()));// timeout receive
	}


	@Test
	@DisplayName("Send and receive ping")
	public void testKeepConnTcpPing4_0_21_10() throws Exception {
		isTested.set(false);
		try {
			ClientConfiguration clientConfig = client.getConfig();
			clientConfig.pingIntervalMs = 150;
			clientConfig.pingTimeoutMs = 100;
			client.start(new TcpHandler(clientConfig) {
				@Override
				public void handle(TcpContext ctx) {
					asyncSuccessHandle.handle(ctx);
				}
			});
		} catch (TimeoutException e) {
			throw new RuntimeException(e);
		}
		TimeUtils.waitSafeMili(10000000);
	}

	@Test
	@DisplayName("Send and receive content body based on instance connection")
	public void testSendInstanceTcpConn4_0_21_10() throws Exception {
		isTested.set(false);
		content.set("testr0n20r202u0x0pfncfyp0hfp0ym02cp0hfp0[0fyk2-90ypx3h0fh0x".getBytes(StandardCharsets.UTF_8));
		ClientConfiguration clientConfig = iClient.getConfig();
		clientConfig.pingIntervalMs = 150;
		clientConfig.pingTimeoutMs = 100;
		doSend();
	}

	@Test
	@DisplayName("Server 4.2 wait conn")
	public void tcpServer() throws Exception {
		server.start(new EchoTcpHandler(server.getConfig()));
		TimeUtils.waitSafeMili(10000000);
	}

}
