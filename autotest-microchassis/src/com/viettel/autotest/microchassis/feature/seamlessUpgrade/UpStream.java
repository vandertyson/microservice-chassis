package com.viettel.autotest.microchassis.feature.seamlessUpgrade;

import com.viettel.autotest.microchassis.lib.sdk.VduProcess;
import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.log.MetricsBenchmark;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.mano.Microchassis;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import io.netty.handler.codec.http.HttpMethod;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author tiennn18
 */
public class UpStream extends VduProcess {
// MANO_AUTHORIZATION_SERVER=http://manoip:321/authen;MANO_ENABLE=false;MANO_METRIC_RECEIVE_URI=http://manoip:321/metr;MANO_RECEIVED_ALARM_URI=http://manoip:321/alarm;VNFC_NOTIFICATION_ENDPOINT=http://manoip:321/notif;SITE=HLC;REGION=VIETNAM-NORTH;VIM=KEANGNAM-F40;NS_INSTANCE_NAME=OCS-PAT;VNF_INSTANCE_NAME=data4g-hlc;VDU_NAME=pcp-gx;VNFC_NAME=pcp-gx-2959hdfo9fyn-189r7t28o
	public static void main(String[] args) throws Exception {
		CommonConfig.JavaConfig.ETC_FOLDER.setDefault("./upStream");
		LogUtils.setupLog4j2();
		//start http server
		Microchassis chassis = new Microchassis().start(null, false);
		HttpClient client = ConnectionManager.getInstance().getClient("httpClient");
		MetricsBenchmark mon = new MetricsBenchmark();
		try {
			client.start(new HttpClientHandler(client.getConfig()){
				@Override
				public void timeoutHandle(String requestID) {
					mon.statisticMetris( "TimeO");
//					logger.error("Tectec "+ requestID);
					super.timeoutHandle(requestID);
				}

				@Override
				public void handle(HttpClientReceiveContext tcpContext) {
					mon.statisticMetris( "Succ");
					super.handle(tcpContext);
				}

				@Override
				public void dropHandle(HttpRequest failedReq, boolean isSent) {
					mon.statisticMetris( isSent ? 1 : 0, "Drop");
//					logger.error("Tecdrop" + failedReq.getMessageId());
					super.dropHandle(failedReq, isSent);
				}
			});
//			TimeUtils.NanoBeacon start = new TimeUtils.NanoBeacon(1);
			while(true) {
				try {
//					client.send(client.createReq(String.valueOf(TimeUtils.nowNano()).getBytes(StandardCharsets.UTF_8), "/hmmm"));
					Map<Endpoint, HttpResponse> endpointMsgMap = client.sendSyncBroadcast(client.createReq(HttpMethod.GET, "/brodTien", String.valueOf(TimeUtils.nowNano()).getBytes(StandardCharsets.UTF_8)));
					System.out.println("Result "+endpointMsgMap);
				} catch (Throwable t){
					logger.error(t, t);
				}
			}
		} catch (Exception e) {
			logger.error(e, e);
		}
	}
}

