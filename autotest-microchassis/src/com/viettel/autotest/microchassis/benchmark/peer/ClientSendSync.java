/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.autotest.microchassis.benchmark.peer;

import com.viettel.autotest.microchassis.benchmark.BenchmarkClientConfiguration;
import com.viettel.autotest.microchassis.ChassisAutotestConst;
import com.viettel.autotest.microchassis.lib.depricatedNeedRefactor.BenchmarkClient;
import com.viettel.autotest.microchassis.lib.generator.PayloadGenerator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author vttek
 */
public class ClientSendSync implements Runnable {

	private static final Logger logger = LogManager.getLogger(ClientSendSync.class);
	private final BenchmarkClient client;
	String host;
	int port;
	private int payloadSize;
	private String path;
	AtomicLong totalSend;
	AtomicLong totalReceive;
	Long totalReqeust;
	AtomicLong fail;
	private int concurent;
	static final AtomicLong reqID = new AtomicLong();
	static final long REQ_RANGE = Long.getLong("reqRange", 100_000_000L);
	static final AtomicLong countDrop = new AtomicLong();
	Map<String, AtomicLong> channelSendCount;
	Map<String, AtomicLong> channelReceiveCount;

	public ClientSendSync(BenchmarkClientConfiguration config,
												String path,
												AtomicLong totalSend,
												AtomicLong totalReceive,

												AtomicLong fail,
												BenchmarkClient client,
												Map<String, AtomicLong> channelSendCount,
												Map<String, AtomicLong> channelReceiveCount) {

		String host = config.getHost();
		int port = config.getPort();
		int payloadSize = config.getPayloadSize();
		int concurent = config.getConcurent();
		Long totalReqeust = config.getTotalReqeust();


		this.host = host;
		this.port = port;
		this.payloadSize = payloadSize;
		this.path = path;
		this.totalSend = totalSend;
		this.totalReceive = totalReceive;
		this.totalReqeust = totalReqeust;
		this.fail = fail;
		this.concurent = concurent;
		this.channelSendCount = channelSendCount;
		this.channelReceiveCount = channelReceiveCount;
		this.client = client;
		client.setCallBack(arg0 -> sendArequest());
	}

	public void sendArequest() {
		long id = REQ_RANGE + reqID.incrementAndGet();
		byte[] arr;
		if (Boolean.getBoolean("useJson")) {
			byte[] body = PayloadGenerator.nextOcsPayload();
			ByteBuf bb = UnpooledByteBufAllocator.DEFAULT.heapBuffer(body.length + ChassisAutotestConst.bytePayload);
			long time = System.nanoTime();
			bb.setLong(ChassisAutotestConst.byteTimeSendFromClient, time);
			bb.setCharSequence(ChassisAutotestConst.byteMessID, String.valueOf(id), StandardCharsets.UTF_8);
			bb.setBytes(ChassisAutotestConst.bytePayload, body);
			bb.resetReaderIndex();
			arr = bb.array();
		} else {
			ByteBuffer bb = ByteBuffer.allocate(payloadSize);
			bb.putLong(ChassisAutotestConst.byteTimeSendFromClient, System.nanoTime());
			byte[] bytes = String.valueOf(id).getBytes(StandardCharsets.UTF_8);
			for (int i = 0; i < bytes.length; i++) {
				bb.put(ChassisAutotestConst.byteMessID + i, bytes[i]);
			}
			arr = bb.array();
		}
		try {
			client.send(arr, String.valueOf(id));
		} catch (Exception e) {
			long incrementAndGet = countDrop.incrementAndGet();
			logger.info("========================DROP TOTAL:" + incrementAndGet + "========================");
			logger.error(e, e);
		}
	}


	@Override
	public void run() {
		try {
			for (int i = 0; i < concurent; i++) {
				sendArequest();
			}
		} catch (Exception e) {
			logger.error(e, e);
		}
	}
}
