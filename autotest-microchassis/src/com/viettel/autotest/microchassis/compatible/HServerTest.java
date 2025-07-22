/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.autotest.microchassis.compatible;

import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.connection.config.ChannelConfiguration;
import com.viettel.vocs.microchassis.connection.config.SslConfiguration;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.log.MetricsBenchmark;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import io.netty.util.ResourceLeakDetector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author vttek
 */
public class HServerTest {

    static final Logger logger = LogManager.getLogger(HServerTest.class);
    static int port = Integer.getInteger("port", 8088);
    static Boolean ssl = Boolean.getBoolean("ssl");
    static String keyPath = System.getProperty("keyPath", "./etc/key_nodes.pem");
    static String certPath = System.getProperty("certPath", "./etc/cert_nodes.pem");
    @Test
    public void sslTest(String[] args) throws Exception {
        LogUtils.setupLog4j2();
        HttpServerOptions options = new HttpServerOptions("1s", Integer.getInteger("port", 9000));
        options.setChannelConfiguration(new ChannelConfiguration().setEpoll(false).setNumWorkerThread(2));
        if (Boolean.parseBoolean(System.getProperty("ssl", "true"))) {
            String certPath = System.getProperty("certPath", "../etc/cert.pem");
            String keyPath = System.getProperty("keyPath", "../etc/key.pem");
            logger.info("SSL configuration. key={}, cert={}", keyPath, certPath);
            SslConfiguration sslConfiguration = new SslConfiguration();
            sslConfiguration.setCertPath(certPath);
            sslConfiguration.setKeyPath(keyPath);
            options.setSslConfiguration(sslConfiguration);
        }
        HttpServer server = options.newServer();
        AtomicBoolean hold = new AtomicBoolean(false);
        server.start(new HttpServerHandler(options){
            @Override
            public void handle(HttpServerContext ctx) {
                try {
                    byte[] bytes =  ctx.getInData();
                    if (logger.isDebugEnabled()) {
                        logger.debug(String.format("[Receive request]{ctx=%s, http2=%s, headers=%s, content=%s}", ctx.getChannelString(), ctx.isHttp2(), ctx.getInMsg().headers(), bytes));
                    }
                    if (ctx.getInPath().contains("hold")) {
                        boolean b = hold.get();
                        if (!b) {
                            hold.compareAndExchange(false, true);
                            logger.error("hold request enable");
                            ctx.send("hold request enable");
                        } else {
                            hold.compareAndExchange(true, false);
                            logger.error("hold request disable");
                            ctx.send("hold request disable");
                        }
                    } else {
                        if (hold.get()) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Holding request {} from {}", ctx.getInPath(), ctx.getChannel());
                            }
                            ctx.decompose();
                            return;
                        }
                        if(logger.isDebugEnabled()) ctx.send(Arrays.toString(bytes) + " from " + InetAddress.getLocalHost().getHostName());
                        else ctx.send(bytes);
                    }
                    MetricsBenchmark.getInstance().statisticMetris(System.nanoTime(), bytes.length, "Server");
                } catch (Exception e) {
                    logger.error(e, e);
                }
            }});
        server.waitServerClose();
    }
    public static void main(String[] args) throws Exception { // included SSLException, InterruptedException
        System.setProperty("io.netty.leakDetection.level", "disabled");
        logger.info(ResourceLeakDetector.getLevel());
        LogUtils.setupLog4j2();
        HttpServer server;
        HttpServerOptions opt = new HttpServerOptions(ChassisConst.SupportVersion.HTTP, port);
        if (ssl && keyPath != null && !keyPath.isEmpty()) {
                opt.setSslConfiguration(new SslConfiguration().setKeyPath(keyPath).setCertPath(certPath));

        }
        server = new HttpServer(opt);
        server.start(new HttpServerHandler(opt){
            @Override
            public void handle(HttpServerContext hctx) {
            try {
                String payload = "Good day. It's " + new Date().toLocaleString();
                if (hctx.isHttp2()) {
                    logger.info("Receive HTTP 2 request. Headers=" + hctx.getInMsg().headers()
                      + ", payload=" + Arrays.toString(hctx.getInData()));
                } else {
                    logger.info("Receive HTTP 1 request. Headers=" + hctx.getInMsg().headers() + ", payload="
                      + Arrays.toString(hctx.getInData()));
                }
                hctx.send(payload);
            } catch (Exception ex) {
                logger.error(ex, ex);
            }
        }});
        TimeUtils.waitUntil(()->server.ch != null,0);
        logger.info("SERVER STARTED");
        server.waitServerClose();
    }
}
