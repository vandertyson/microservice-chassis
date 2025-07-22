package com.viettel.autotest.microchassis.feature.protoTest;

import com.viettel.vocs.common.os.TimeUtils;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TcpReconnectTest extends TcpEchoTest {
	@Test
	@Order(8)
	public void reconnectTest() throws Exception {
		testEchoBody();
		server.restart(echoTcpHandler);
		assertTrue(TimeUtils.completeUnder(() -> client.countConnected() > 0, 30000));
		logger.info("Client status {} conns", client.countConnected());
	}
}
