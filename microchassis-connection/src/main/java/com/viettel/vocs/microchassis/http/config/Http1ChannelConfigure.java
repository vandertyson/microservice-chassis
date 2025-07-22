package com.viettel.vocs.microchassis.http.config;

import com.viettel.vocs.microchassis.base.ChassisConfig;

/**
 * @author tiennn18
 */
public class Http1ChannelConfigure {
	public int maxContentLength = ChassisConfig.ConnectionConfig.Http1Config.Http1MAX_CONTENT_LENGTH.getInt();
	public boolean keepAlive = false;

	public Http1ChannelConfigure setMaxContentLength(int maxContentLength) {
		this.maxContentLength = maxContentLength;
		return this;
	}
}
