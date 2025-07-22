/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.autotest.microchassis.lib.depricatedNeedRefactor;

import com.viettel.vocs.common.log.MetricsBenchmark;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author vttek
 */
public class AsyncClientHandler {

    private static final Logger logger = LogManager.getLogger(AsyncClientHandler.class);
//    static final ThreadLocal<MessageDigest> md5local = ThreadLocal.withInitial(() -> {
//        try {
//            return MessageDigest.getInstance("MD5");
//        } catch (NoSuchAlgorithmException ex) {
//            logger.error(ex, ex);
//            return null;
//        }
//    });
    AtomicLong totalReceive;
    Long totalReqeust;
    AtomicLong fail;
    Map<String, AtomicLong> channelReceiveCount;
    String serverName;

    public AsyncClientHandler(AtomicLong totalReceive, Long totalReqeust, AtomicLong fail, Map<String, AtomicLong> channelReceiveCount, String serverName) {
        this.totalReceive = totalReceive;
        this.totalReqeust = totalReqeust;
        this.fail = fail;
        this.channelReceiveCount = channelReceiveCount;
        this.serverName = serverName;
    }

    public void timeOutHandler(String requestID) {
        logger.info("****************Timeout requestID " + requestID + " ****************");
        fail.incrementAndGet();
        totalReceive.incrementAndGet();
    }

    public void handle(Msg msg) {
        long now = System.nanoTime();
        String id = msg.getMessageId();
        String checkId = null;
        long sentTime = 0L;
        if (sentTime == -1) {
            logger.error("[ID: " + id + "]Wrong time");
            System.exit(0);
            return;
        }
        if (!id.equals(checkId)) {
            logger.error("[ID: " + id + "]Wrong ID. HeaderID: " + id + "|ContentID: " + checkId);
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
        MetricsBenchmark.getInstance().statisticMetris(sentTime, msg.readableBytes(), serverName);
        long receive = totalReceive.incrementAndGet();
        if (receive == totalReqeust) {
            logger.info("Finish receive " + receive);
        }
    }
}
