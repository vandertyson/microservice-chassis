package com.viettel.autotest.microchassis.feature.seamlessUpgrade;

import com.viettel.autotest.microchassis.lib.sdk.VduProcess;
import com.viettel.vocs.microchassis.tcp.client.TcpInstanceClient;

import java.nio.charset.StandardCharsets;

public class CallNotifyDownStream extends VduProcess {
	// MANO_AUTHORIZATION_SERVER=http://manoip:321/authen;MANO_ENABLE=false;MANO_METRIC_RECEIVE_URI=http://manoip:321/metr;MANO_RECEIVED_ALARM_URI=http://manoip:321/alarm;VNFC_NOTIFICATION_ENDPOINT=http://manoip:321/notif;SITE=HLC;REGION=VIETNAM-NORTH;VIM=KEANGNAM-F40;NS_INSTANCE_NAME=OCS-PAT;VNF_INSTANCE_NAME=data4g-hlc;VDU_NAME=pcp-gx;VNFC_NAME=pcp-gx-2959hdfo9fyn-189r7t28o
	public static void main(String[] args) throws Exception {
		//start http server
		TcpInstanceClient client = TcpInstanceClient.createInstance("ehehe",
			ctx -> System.out.println("res "+ ctx.getInMsg().toStringUTF8()),
			timeOut -> System.out.println("timeOut "+ timeOut));
		client.send("172.16.34.125", 9099, client.createReq("ohoho".getBytes(StandardCharsets.UTF_8), "/notify"));
	}
}
