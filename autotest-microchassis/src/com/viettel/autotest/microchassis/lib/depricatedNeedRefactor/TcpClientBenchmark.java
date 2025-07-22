package com.viettel.autotest.microchassis.lib.depricatedNeedRefactor;

import com.viettel.autotest.microchassis.connection.config.GeneratorServerConfiguration;
import com.viettel.autotest.microchassis.lib.generator.TcpPayloadGenerator;
import com.viettel.autotest.microchassis.benchmark.peer.ClientBenchmark;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.tcp.client.TcpClient;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import io.netty.buffer.ByteBufAllocator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TcpClientBenchmark extends ClientBenchmark {
	public static final int port = 9000;
	GeneratorServerConfiguration gConfig;
	protected void setUpClient() {
		try {
			ClientConfiguration config = new ClientConfiguration("localhost", port, "test");
//			config.onSupport(HttpVersion.HTTP_1_1, h1c-> h1c.setMaxContentLength(PayloadGenerator.fakePayloadSize*2));
//			config.setAutoDecompose(false);
			(client = new TcpClient(config)).start(new TcpHandler(config) {
				public void handle(TcpContext ctx) {
					countAsyncReceive.incrementAndGet();
					if(countAsyncReceive.get()%10==0) System.out.println(countAsyncReceive.get());
					if(logger.isDebugEnabled()) {
//						HttpResponse inMsg = ctx.getInMsg();
//						logger.info("Receive async response " + countAsyncReceive.get() + "|Status: " + inMsg.status() + "|head: " + inMsg.headers() + "|msg: " + inMsg.toString(StandardCharsets.UTF_8));
					}
				}
			});
			logger.info("===================CLIENT FOR TEST CONNECTED===================");
		} catch (Exception e) {
			logger.error(e, e);
		}
//		FakeTPSGenerator.getInstance(); // autorun
	}
	TcpPayloadGenerator generator;
	@Override
	protected Msg makeRequest(ByteBufAllocator allocator) {
		return generator.generateRandomBinaryRequest(allocator, gConfig.getFakePayloadSize());
	}

	@Override
	protected Msg makeRequest() {
		return generator.generateRandomBinaryRequest(gConfig.getFakePayloadSize());
	}

	@Test
	@Order(1)
	public void testAsyncRequest() {
		long start = TimeUtils.nowNano();
		try {
			for (int i = 0; i < numberOfRequest; i++) {
//				long myid = id.incrementAndGet();
//				Http1Request req = ;
				client.send(makeRequest());
//				logger.info("Send request " + myid + " msgId " + req.getMessageId());
			}
		} catch (Exception e) {
			logger.error(e, e);
		}
		assert TimeUtils.completeUnder(() -> numberOfRequest == countAsyncReceive.get(), timeout + numberOfRequest * 100);
		System.out.println("Completed test in " + TimeUtils.miliPassed(start) + "ms for "+ countAsyncReceive.get() + " requests");
	}

	protected TcpClient getClient() {
		return (TcpClient) client;
	}

	/**
	 * Test of onDisconnect method, of class HttpServerImpl.
	 */
	@Test
	@Order(2)
	public void testSyncRequest() {
		int count = 0;
		try {
			for (int i = 0; i < numberOfRequest; i++) {
				Msg rq = makeRequest();
				Msg sendSync = getClient().sendSync(rq);
				if (sendSync == null) Assertions.fail("Time out sync request");
				if (!Arrays.equals(sendSync.getContent(), rq.getContent()))
					Assertions.fail("Server reponse " + sendSync.headers().getStatus() + "|" + sendSync.toString(StandardCharsets.UTF_8));
				logger.info("Receive sync response|" + sendSync.headers());
				countSyncReceive.incrementAndGet();
				sendSync.decompose();
			}
		} catch (Exception e) {
			logger.error(e, e);
		}
		Assertions.assertEquals(numberOfRequest, count);
	}

	private boolean sendSync() {
		try {
			Msg req = makeRequest();
			String reqMsgId = req.getMessageId();
			Msg res = getClient().sendSync(req);
			if (res == null) {
				Assertions.fail("Time out sync request");
				return false;
			}
			String resMsgId = res.getMessageId();
			if (!Arrays.equals(res.getContent(), req.getContent())) {
				Assertions.fail("Server reponse " + res.headers().getStatus() + "|" + res.toString(StandardCharsets.UTF_8));
				return false;
			}

			Assertions.assertTrue(resMsgId != null && !resMsgId.isEmpty());
			Assertions.assertEquals(reqMsgId, resMsgId);
			String resSContent = res.toString(StandardCharsets.UTF_8);
			String reqSContent = req.toString(StandardCharsets.UTF_8);
			logger.info("Receive sync response|Headers " + res.headers() + "|Content :" + resSContent);
			Assertions.assertEquals(reqSContent, resSContent);
			countSyncReceive.incrementAndGet();
			res.decompose();
			return true;
		} catch (Exception e) {
			logger.error("Send sync error", e);
			Assertions.fail(e);
			return false;
		}
	}

	private boolean sendAsync() {
		try {
			getClient().send(makeRequest());
			countAsyncSend.incrementAndGet();
		} catch (Exception e) {
			logger.error("Send async error", e);
			Assertions.fail(e);
			return false;
		}
		return true;
	}

	@Test
	@Order(4)
	public void mixSyncAndAsyncRequest() {
		threadPool.execute(() -> {
			for (int i = 0; i < numberOfRequest; i++) sendSync();
			if (Math.random() > 0.5) sendSync();
			else sendAsync();
		});
		threadPool.execute(() -> {
			for (long i = numberOfRequest; i < numberOfRequest + numberOfRequest; i++) sendAsync();
			if (Math.random() > 0.5) sendSync();
			else sendAsync();
		});
		logger.info("Finish send " + numberOfRequest + " request");
		long t = System.currentTimeMillis();
		while (countAsyncReceive.get() != countAsyncSend.get()
			|| countSyncReceive.get() + countAsyncReceive.get() != numberOfRequest) {
			TimeUtils.waitSafeMili(1000);
			if (System.currentTimeMillis() - t > (numberOfRequest * timeout)) {
				Assertions.fail("Timeout");
				break;
			}
		}
		t = System.currentTimeMillis();
		while (countAsyncSend.get() != numberOfRequest || countAsyncReceive.get() != numberOfRequest || countSyncReceive.get() != numberOfRequest ) {
			TimeUtils.waitSafeMili(500);
			System.out.printf("Status: As %d A %d S %d/%d\n", countAsyncSend.get(), countAsyncReceive.get(), countSyncReceive.get(), numberOfRequest);
			if (System.currentTimeMillis() - t > (5 * timeout)) {
				Assertions.fail("Timeout");
				break;
			}
		}
	}
	@Test
	@Order(10)
	public void testMonitorChannelCheck() {
		// start this test first, wait 10s then start server
		System.out.println("Please open server at localhost:" + port);
		assert TimeUtils.completeUnder(()-> {
//			System.out.printf(" %d",client.countConnected());
			return client.countConnected() > 0;
		}, 100000);
		TimeUtils.waitSafeMili(10000);
	}
}
