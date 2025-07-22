package com.viettel.autotest.microchassis.feature.protoTest;

import com.viettel.autotest.microchassis.lib.SelfTestInstance;
import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.codec.handler.tcp.EchoTcpHandler;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
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
public class TcpInstanceEchoTest extends SelfTestInstance {
	static {
		CommonConfig.InstanceInfo.AUTHEN_SERVICE_NAME.setDefault("*");
	}
	static TcpServer server;
	static TcpInstanceClient client;
	static {
		try {
			server = new TcpServer(new ServerConfiguration("tcpServer"));
			server.start(new EchoTcpHandler(server.getConfig()));
			client = TcpInstanceClient.createInstance("tcpTest", tctx -> checkResult(tctx.getInData()), msgId -> System.out.println("Timed out msg: "+ msgId));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	static void checkResult(byte[] incomData){
		//				System.out.println("Echoed: " + new String(incomData, StandardCharsets.UTF_8));
		//				assert Arrays.equals(incomData, content);
		System.out.println("Client received length: "+ incomData.length);
		assert Arrays.equals(incomData, content.get());
		isTested.set(true);
	}

	private Msg createTestMsg(TcpInstanceClient client){
		return client.createReq(content.get(), "/tiennn18", "aloalo1234");
	}

	private static void prepare(){
		isTested.set(false);
		content.set("testr0n20r202u0x0pfncfyp0hfp0ym02cp0hfp0[0fyk2-90ypx3h0fh0x".getBytes(StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("Send and receive sync msg")
	public void testSyncEmpty() {
		prepare();
		try {
			Msg msg = client.sendSync("localhost", 9000, createTestMsg(client));
			System.out.println("Client sent length: "+ content.get().length);
			checkResult(msg.getContent());
		}catch (Exception e){
			System.out.println("Send error: "+e.getMessage());
		}
		Assertions.assertDoesNotThrow(()-> TimeUtils.waitUntil(isTested::get, ChassisConfig.ConnectionConfig.DEFAULT_SEND_TIMEOUT_MS.get()));// timeout receive
	}

	@Test @DisplayName("Send and receive async msg")
	public void testAsyncBody(){
		prepare();
		try {
			client.send("localhost", 9000, createTestMsg(client));
			System.out.println("Client sent length: "+ content.get().length);
		}catch (Exception e){
			System.out.println("Send error: "+e.getMessage());
		}
		Assertions.assertDoesNotThrow(()-> TimeUtils.waitUntil(isTested::get, ChassisConfig.ConnectionConfig.DEFAULT_SEND_TIMEOUT_MS.get()));// timeout receive
	}
	@Test @DisplayName("Send and receive sync msg to network IP")
	public void testIpSyncBody(){
		prepare();
		try {

			Msg msg = client.sendSync("172.16.34.125", 9000, createTestMsg(client));
			System.out.println("Client sent length: "+ content.get().length);
			checkResult(msg.getContent());
		}catch (Exception e){
			System.out.println("Send error: "+e.getMessage());
		}
		Assertions.assertDoesNotThrow(()-> TimeUtils.waitUntil(isTested::get, ChassisConfig.ConnectionConfig.DEFAULT_SEND_TIMEOUT_MS.get()));// timeout receive
	}
	@Test @DisplayName("Send and receive sync msg to not existed IP")
	public void testDumpIpSyncBody(){
		prepare();
		try {
			Msg msg = client.sendSync("8.8.8.8", 9000, createTestMsg(client));
			System.out.println("Client sent length: "+ content.get().length);
			checkResult(msg.getContent());
		}catch (Exception e){
			System.out.println("Send error: "+e.getMessage());
		}
		Assertions.assertThrows(TimeoutException.class, ()-> TimeUtils.waitUntil(isTested::get, ChassisConfig.ConnectionConfig.DEFAULT_SEND_TIMEOUT_MS.get()));// timeout receive
	}
}

