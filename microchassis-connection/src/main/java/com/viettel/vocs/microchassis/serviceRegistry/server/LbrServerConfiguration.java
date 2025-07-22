package com.viettel.vocs.microchassis.serviceRegistry.server;

import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;

/**
 * @author tiennn18
 */
public class LbrServerConfiguration extends ServerConfiguration {
	public static final int DNS_UPDATE_INTERVAL_MS = 1000;
	public int broadcastIntervalMs;

	public LbrServerConfiguration() {
		this("localhost", 11000, 2000);
	}
//	public LbrServerConfiguration(Properties properties) {
//		this(properties.getProperty("id"), Integer.parseInt(properties.getProperty("port")), 2000);
//		this.channelConfiguration = new ChannelConfiguration(properties);
//	}
	public LbrServerConfiguration(String name, int port, int broadcastIntervalMs) {
		super(name, port, ChassisConst.STANDALONE_LBR_ID, Integer.MAX_VALUE, 1, Integer.MAX_VALUE, "fullmesh");
		this.broadcastIntervalMs = broadcastIntervalMs;
	}
}
