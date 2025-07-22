package com.viettel.vocs.microchassis.fakeCgw;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author tiennn18
 */
public class FakeCgwTest {
	public static final Logger logger = LogManager.getLogger(FakeCgwTest.class);
	private static final ConnectionManager connectionManager = ConnectionManager.getInstance();
	@Test
	void selfCallH2() throws Exception {
		try {
			CommonConfig.JavaConfig.CONFIG_FOLDER.setDefault("/home/vht/Projecs/javawork/microservice-chassis-1m/microchassis-connection/src/test/java/com/viettel/vocs/microchassis/fakeCgw/");
			connectionManager.loadYmlConfig();
		} catch (Throwable ex) {
			logger.error(ex.getMessage(), ex);
			throw ex;
		}
		HttpServer server = connectionManager.getServer("ocs-hserver");
		server.start(new HttpServerHandler(server.getConfig()));
		HttpClient client = connectionManager.getClient("h2client");
		String content = "Hello ok";
		byte[] payload = content.getBytes(StandardCharsets.UTF_8);
		HttpResponse response = client.sendSync(client.createReq(payload.length, HttpMethod.GET, "/tiennn",  payload));
		assertEquals(content, response.toString(StandardCharsets.UTF_8));
		assertTrue(client.isConnected());
		assertEquals(1, server.countConnected());
	}
	@Test
	void listenServerMultiProto() throws Exception {
		try {
			CommonConfig.JavaConfig.CONFIG_FOLDER.setDefault("/home/vht/Projecs/javawork/vocs/microservice-chassis/etc/");
			connectionManager.loadYmlConfig();
		} catch (Throwable ex) {
			logger.error(ex.getMessage(), ex);
			throw ex;
		}
		HttpServer server = connectionManager.getServer("tiennn-http");
		server.start(new HttpServerHandler(server.getConfig()));
		TimeUtils.waitSafeMili(10000000);
	}
}
