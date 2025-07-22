package com.viettel.autotest.microchassis.pcp;

import com.viettel.autotest.microchassis.lib.sdk.VduProcess;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.client.HttpInstanceClient;

/**
 * @author tiennn18
 */
public class Pcp extends VduProcess {
// MANO_AUTHORIZATION_SERVER=http://manoip:321/authen;MANO_ENABLE=false;MANO_METRIC_RECEIVE_URI=http://manoip:321/metr;MANO_RECEIVED_ALARM_URI=http://manoip:321/alarm;VNFC_NOTIFICATION_ENDPOINT=http://manoip:321/notif;SITE=HLC;REGION=VIETNAM-NORTH;VIM=KEANGNAM-F40;NS_INSTANCE_NAME=OCS-PAT;VNF_INSTANCE_NAME=data4g-hlc;VDU_NAME=pcp-gx;VNFC_NAME=pcp-gx-2959hdfo9fyn-189r7t28o

	public static void main(String[] args) throws Exception {
		run("pcp");
		HttpClient httpClient = ConnectionManager.getInstance().getClient("h1client");
		HttpInstanceClient defaultHttpInstance = ConnectionManager.getDefaultHttpInstance();
		try {
			httpClient.start(new HttpClientHandler(httpClient.getConfig()));
		} catch (Exception e) {
			logger.error(e, e);
		}
	}
}
