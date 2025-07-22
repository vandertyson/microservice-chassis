package com.viettel.autotest.microchassis.abm;

import com.viettel.autotest.microchassis.lib.sdk.VduProcess;

/**
 * @author tiennn18
 */
public class Abm extends VduProcess {
	// MANO_AUTHORIZATION_SERVER=http://manoip:321/authen;MANO_ENABLE=false;MANO_METRIC_RECEIVE_URI=http://manoip:321/metr;MANO_RECEIVED_ALARM_URI=http://manoip:321/alarm;NS_INSTANCE_NAME=OCS-PAT;REGION=VIETNAM-NORTH;SITE=HLC;VDU_NAME=abm;VIM=KEANGNAM-F40;VNF_INSTANCE_NAME=data4g-hlc;VNFC_NAME=abm-2959hdfo9fyn-189r7t28o;VNFC_NOTIFICATION_ENDPOINT=http://manoip:321/notif
	public static void main(String[] args) throws Exception {
		run("abm");
	}
}
