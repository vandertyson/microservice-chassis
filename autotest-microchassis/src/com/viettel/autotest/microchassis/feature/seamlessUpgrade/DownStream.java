package com.viettel.autotest.microchassis.feature.seamlessUpgrade;

import com.viettel.autotest.microchassis.lib.sdk.VduProcess;
import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.log.MetricsBenchmark;
import com.viettel.vocs.mano.Microchassis;
import com.viettel.vocs.microchassis.codec.context.CtxHolder;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.connection.server.ServerChannelAttribute;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.metrics.MetricCollector;
import com.viettel.vocs.microchassis.metrics.SeriesType;
import org.apache.commons.lang3.tuple.MutablePair;

/**
 * @author tiennn18
 */
public class DownStream extends VduProcess {
// MANO_AUTHORIZATION_SERVER=http://manoip:321/authen;MANO_ENABLE=false;MANO_METRIC_RECEIVE_URI=http://manoip:321/metr;MANO_RECEIVED_ALARM_URI=http://manoip:321/alarm;VNFC_NOTIFICATION_ENDPOINT=http://manoip:321/notif;SITE=HLC;REGION=VIETNAM-NORTH;VIM=KEANGNAM-F40;NS_INSTANCE_NAME=OCS-PAT;VNF_INSTANCE_NAME=data4g-hlc;VDU_NAME=pcp-gx;VNFC_NAME=pcp-gx-2959hdfo9fyn-189r7t28o

	public static void start(String configPath) throws Exception {
		LogUtils.setupLog4j2();
		char podId = configPath.charAt(configPath.length() - 1);
		//start http server
		CommonConfig.JavaConfig.ETC_FOLDER.setDefault(configPath);
		Microchassis chassis = new Microchassis();
		MetricsBenchmark mon = new MetricsBenchmark();
		try {
			chassis.start(null, false);
//			chassis.openServiceMano(HttpManoServerHandler::new, null);
			chassis.openService("httpServer",
				true, // wait port up
				config -> new HttpServerHandler((HttpServerOptions) config){
					@Override
					public void handle(HttpServerContext tcpContext) {
						mon.statisticMetris( "Succ"+podId);
						if(tcpContext.getInPath().equals("/notify")) {
							ServerChannelAttribute.getServer(tcpContext.getChannel()).notifyStop();
							tcpContext.send("Notified bebe");
						} else {
							String stringUTF8 = tcpContext.getInMsg().toStringUTF8();
							logger.info("REceive "+ stringUTF8 + "_"+ CtxHolder.getIpFrom(tcpContext.getChannel()));
							tcpContext.send("OkBebe_" + stringUTF8);
						}
					}

					@Override
					public void dropHandle(HttpRequest failedReq, boolean isSent) {
						mon.statisticMetris(isSent ? 1 : 0, "Drop");
						super.dropHandle(failedReq, isSent);
					}
				}, // main handler
				NettyServer::notifyStop, // shutdown hook thread
				server -> { // after service up
					MetricCollector.registerApp();
					MetricCollector.getMetric().set("testMe", SeriesType.avgT, 92332.2893629d, new MutablePair<>("tiennn18", "tiennn19"));
				});
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	public static void main(String[] args) throws Exception {
		start("./downStream" + args[0]);
	}
}