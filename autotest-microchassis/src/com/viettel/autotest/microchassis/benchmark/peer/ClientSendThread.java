/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.autotest.microchassis.benchmark.peer;

import com.viettel.autotest.microchassis.ChassisAutotestConst;
import com.viettel.autotest.microchassis.lib.depricatedNeedRefactor.BenchmarkClient;
import com.viettel.vocs.common.log.MetricsBenchmark;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * @author vttek
 */
public class ClientSendThread implements Runnable {

    private static final Logger logger = LogManager.getLogger(ClientSendThread.class);
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

    public ClientSendThread(String host,
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
            int lastBreak = 0;
            while (true) {
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
                    client.send(bb.array(), String.valueOf(id));
                } catch (Exception e) {
                    long incrementAndGet = countDrop.incrementAndGet();
//                    logger.info("========================DROP TOTAL: {}========================", incrementAndGet);
                    logger.error(e);
                }

                long sent = totalSend.incrementAndGet();
                long c = counter.incrementAndGet();
                long now = System.currentTimeMillis();

                if (concurent > 0) {
                    while (
//                      Boolean.getBoolean("seflCheck") ? sent - client.getCurrentResponseCount() > concurent :
                        sent - totalReceive.get() > concurent) {
//                    while (sent - client.getCurrentResponseCount() > concurent) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("==========concurrent========="
//                                    + "|CurrentAL: " + client.getCurrentResponseCount()
                                    + "|Drop: " + countDrop.get());
                        }
                        LockSupport.parkNanos(10);
                        if (System.currentTimeMillis() - now > 5000) {
//                            logger.info("Sent {}|Received {}|CurrentAL {}|Concurrent {}|Drop {}",
//                                    sent, totalReceive.get(), client.getCurrentResponseCount(), concurent, countDrop.get());
                            lastBreak++;
                            if (lastBreak > 5) {
                                logger.info("==============ResetCounter=============");
                                totalSend.set(0);
                                totalReceive.set(0);
//                                totalSend.set(client.getCurrentResponseCount());
                                lastBreak = 0;
                            }
                            break;
                        }
                    }
                }

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
//
                if (System.currentTimeMillis() - startLog > interval) {
//                    logger.info("Sent: {}|Handler: {}|Fail: {}|Client: {}",
//                            sent, totalReceive.get(), fail.get(), client.getCurrentResponseCount()
//                    );
                    startLog = System.currentTimeMillis();
                }
                if (totalReqeust > 0 && sent >= totalReqeust) {
//                    logger.info("Finish send {}", totalReqeust);
//                    channelSendCount.entrySet().forEach(entry -> {
//                        logger.info("Channel {}|Received: {}", entry.getKey(), entry.getValue().get());
//                    });
                    break;
                }
            }
        } catch (Exception e) {
            logger.error(e, e);
        }
    }
}
