package com.viettel.autotest.microchassis.compatible.standardPeer;

import com.viettel.autotest.microchassis.ChassisAutotestConfig;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;

/**
 * @author tiennn18
 */
public class EchoHttpServer {
	public static void main(String[] args) throws Exception {
		LogUtils.setupLog4j2();
		HttpServer chassisServer = new HttpServerOptions("hServer", ChassisAutotestConfig.thirdPartyPort).newServer();
		chassisServer.start(new HttpServerHandler(chassisServer.getConfig()));
	}
}
