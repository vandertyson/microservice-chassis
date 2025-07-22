package com.viettel.autotest.microchassis.benchmark.peer;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.connection.client.NettyClient;
import io.netty.buffer.ByteBufAllocator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public abstract class ClientBenchmark extends ConnectionBenchmark {

	protected static NettyClient client;
	protected abstract void setUpClient();

	protected abstract ChassisMgrableMsg makeRequest(ByteBufAllocator allocator);
	protected ChassisMgrableMsg makeRequest(){
		return makeRequest(client.getBytebufAllocator());
	}
	@BeforeEach
	public void setUpTest() {
		countAsyncReceive.set(0);
		countSyncReceive.set(0);
		countAsyncSend.set(0);
		countSyncSend.set(0);
	}
	@AfterEach
	public void tearDownTest() {

	}

	@AfterAll
	public void tearDownClass() {
		try {
			client.close();
		} catch (Exception e) {
			logger.error(e, e);
		}
	}
	@BeforeAll
	public void setUpClass(){
		try {
			logger.info("Setting up");
			setUpClient();
		} catch (Exception e) {
			logger.error(e, e);
		}
	}
}
