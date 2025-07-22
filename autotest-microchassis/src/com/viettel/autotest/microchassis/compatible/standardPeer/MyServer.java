package com.viettel.autotest.microchassis.compatible.standardPeer;

import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;

public class MyServer {
    static Logger logger = LogManager.getLogger(MyServer.class);

    public static void main(String[] args) {
        LogUtils.setupLog4j2();
        try {
            HttpServerOptions options = new HttpServerOptions("test", 9443);
//            options.enable(ChassisConst.SupportVersion.HTTP_2_0);
            HttpServer server = new HttpServer(options);
            server.start(new HttpServerHandler(server.getConfig()) {
                public void handle(HttpServerContext ctx) {
                    try {
                        HttpRequest inMsg = ctx.getInMsg();
                        if (inMsg.readableBytes() > 0) {
                            ctx.send(inMsg.toString(StandardCharsets.UTF_8));
                        } else {
                            ctx.send("Hi");
                        }
                    } catch (Exception e) {
                        logger.error(e, e);
                    }
                }
            });
            logger.info("===================SERVER FOR TEST STARTED===================");
            server.ch.closeFuture().await();
        } catch (Exception e) {
            logger.error(e, e);
        }
    }
}