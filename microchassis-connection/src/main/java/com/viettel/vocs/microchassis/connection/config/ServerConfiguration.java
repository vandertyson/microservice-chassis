/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection.config;

import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisConst;

import java.util.Objects;

/**
 * @author vttek
 */
public class ServerConfiguration extends PeerConfig {
	public final int maxHost;
	public int bindRetry = 3;

	protected boolean defaultMd5 = ChassisConfig.PeerHandlerConfig.ChecksumConfig.DEFAULT_SERVER_USE_MD5.get();
	public RateLimiterConfig rateLimiter;

	public ServerConfiguration(String serverId) {
		this(serverId, 9000, ChassisConst.STANDALONE_LBR_ID);
	}
	public ServerConfiguration() {
		this("noNameServer", 9000, ChassisConst.STANDALONE_LBR_ID);
	}


	@Override
	public boolean diff(PeerConfig obj) {
		if (!(obj instanceof ServerConfiguration)) return true;
		ServerConfiguration o = (ServerConfiguration) obj;
		return o.maxHost != maxHost
			|| o.defaultMd5 != defaultMd5
			|| o.bindRetry != bindRetry
			|| !Objects.equals(o.rateLimiter, rateLimiter)
			;
	}


	public ServerConfiguration(String id, int port, String lbrId, int maxOpenConnection, int minOpenConnection, int maxHostConnectable, String limitConnectionMode) {
		super(id, null, lbrId, port, limitConnectionMode, minOpenConnection, maxOpenConnection);
		this.maxHost = maxHostConnectable > 0 ? maxHostConnectable : maxConnection; // remove negative
		rateLimiter = new RateLimiterConfig();
		if (defaultMd5) setMd5CheckSum();
	}

	public ServerConfiguration(String id, int port) {
		this(id, port, ChassisConst.STANDALONE_LBR_ID, 0, 0, 0, FULL_MESH_MODE); // 0 mean default (no limits)
	}

	public ServerConfiguration(String id, int port, String lbrId) {
		this(id, port, lbrId, 0, 0, 0, FULL_MESH_MODE); // 0 mean default (no limits)
	}

	//    public ServerConfiguration(Properties properties) {
//        this(properties.getProperty("id"), Integer.parseInt(properties.getProperty("port")), properties.getProperty("lbrId", LBRAdapter.STANDALONE_LBR_ID));
//        storedProperties = properties;
//        this.channelConfiguration = new ChannelConfiguration(properties);
//    }
//    public static ServerConfiguration newFromProperties(Properties properties){
//        return new ServerConfiguration(properties);
//    }
	public ServerConfiguration setSendTimeoutMs(long sendTimeoutMs) {
		this.sendTimeoutMs = sendTimeoutMs;
		return this;
	}
	public ServerConfiguration setRateLimiter(RateLimiterConfig rateLimiter) {
		this.rateLimiter = rateLimiter;
		return this;
	}

	public ServerConfiguration setBindRetry(int bindRetry) {
		this.bindRetry = bindRetry;
		return this;
	}

	@Override
	public String getProtocolName() {
		return ChassisConst.SupportVersion.TCP;
	}

	public String getProtocolShortName() {
		return ChassisConst.SupportVersion.TCP_SHORT;
	}
}
