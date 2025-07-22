/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.autotest.microchassis.benchmark.peer;

import com.viettel.autotest.microchassis.ChassisAutotestConst;
import com.viettel.autotest.microchassis.lib.depricatedNeedRefactor.BenchmarkClient;
import com.viettel.vocs.common.log.MetricsBenchmark;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * @author vttek
 */
public class SyncThread implements Runnable {

    private static final Logger logger = LogManager.getLogger(SyncThread.class);
    private final BenchmarkClient client;
    String host;
    int port;
    private int payloadSize;
    private byte[] payload;
    private String path;
    AtomicLong totalSend;
    AtomicLong totalReceive;
    Long totalReqeust;
    AtomicLong fail;
    static final AtomicLong reqID = new AtomicLong();
    static final long REQ_RANGE = Long.getLong("reqRange", 0);
    static final AtomicLong countDrop = new AtomicLong();

    static long maxTps = Long.getLong("maxTPS", 1);
    static AtomicLong start = new AtomicLong(System.nanoTime() - 1);
    static long delta = Long.getLong("delta", 500000);
    static AtomicLong counter = new AtomicLong(0);
    static MetricsBenchmark mb = new MetricsBenchmark();
    private int concurent;
    private long interval = Long.getLong("interval", 10000);
    private long startLog = System.currentTimeMillis();
    Map<String, AtomicLong> channelSendCount;
    Map<String, AtomicLong> channelReceiveCount;

    public SyncThread(String host,
                      int port,
                      int payloadSize,
                      int concurent,
                      String path,
                      AtomicLong totalSend,
                      AtomicLong totalReceive,
                      Long totalReqeust,
                      AtomicLong fail,
                      BenchmarkClient client,
                      Map<String, AtomicLong> channelSendCount,
                      Map<String, AtomicLong> channelReceiveCount) {
        this.host = host;
        this.port = port;
        this.payloadSize = payloadSize;
        this.path = path;
        this.totalSend = totalSend;
        this.totalReceive = totalReceive;
        this.totalReqeust = totalReqeust;
        this.fail = fail;
        payload = new byte[payloadSize];
        this.concurent = concurent;
        this.channelSendCount = channelSendCount;
        this.channelReceiveCount = channelReceiveCount;
        this.client = client;
    }

    @Override
    public void run() {
        try {

            for (int i = 0; i < concurent; i++) {
                Executors.newSingleThreadExecutor().execute(() -> {
                    while (true) {
                        try {
                            long id = REQ_RANGE + reqID.incrementAndGet();
                            byte[] bba = new byte[payloadSize];
                            ByteBuf bb = Unpooled.wrappedBuffer(bba);
                            bb.clear();
                            bb.writeBytes(payload);
                            long time = System.nanoTime();
                            bb.setLong(ChassisAutotestConst.byteTimeSendFromClient, time);
                            bb.setCharSequence(ChassisAutotestConst.byteMessID, id + "", StandardCharsets.UTF_8);
                            bb.resetReaderIndex();

                            try {
                                ChassisMgrableMsg response = client.sendSync(bb.array(), String.valueOf(id));
                                long sent = totalSend.incrementAndGet();
                                long c = counter.incrementAndGet();
                                long now = System.currentTimeMillis();
                                statistics(response);
                                //control tps o day
                                if (maxTps > 0) {
                                    while (c * 1_000_000_000.0 / (System.nanoTime() - start.get()) > maxTps) {
                                        LockSupport.parkNanos(10);
                                    }
                                    long x = start.get();
                                    if (System.nanoTime() - x >= delta) {
                                        counter.set(0);
                                        start.compareAndExchange(x, System.nanoTime() - 1);
                                    }
                                }
                            } catch (Exception e) {
                                long incrementAndGet = countDrop.incrementAndGet();
                                logger.info("========================DROP TOTAL" + incrementAndGet);
                                logger.error(e);
                            }
                        } catch (Exception ex) {
                            logger.error(ex, ex);
                        }
                    }

                });
            }
        } catch (Exception e) {
            logger.error(e, e);
        }
    }

    private void statistics(ChassisMgrableMsg msg) {
        long now = System.nanoTime();
        String id = msg.getMessageId();
        long sentTime = msg.getSentTime();
        if (sentTime == -1) {
            logger.error("[ID: " + id + "]Wrong time");
            System.exit(0);
            return;
        }
        String checkID = msg.getMessageId();
        if (!checkID.equals(id)) {
            logger.error("[ID: " + id + "]Wrong ID. HeaderID: " + id + "|ContentID: " + checkID);
            System.exit(0);
        }
        long latency = now - sentTime;
        if (logger.isDebugEnabled()) {
            logger.debug("[ID: " + id + "]latency " + latency / 1e6 + " ms|Size: " + msg.readableBytes());
        }
        if (latency < 0) {
            logger.error("[ID: " + id + "]latency < 0: " + latency / 1e6 + " ms");
            System.exit(0);
        }
        MetricsBenchmark.getInstance().statisticMetris(sentTime, msg.readableBytes(), "Response");
        long receive = totalReceive.incrementAndGet();
        if (receive == totalReqeust) {
            logger.info("Finish receive " + receive);
        }
    }
}
