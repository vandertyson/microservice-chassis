/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection.config;

import com.viettel.vocs.common.IDfy;
import com.viettel.vocs.common.datatype.StringUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.RR;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author vttek
 */
public class ClientConfiguration extends PeerConfig implements IDfy {
	public boolean instance = false;

	public final int minHost;
	public String authenServiceName;

	public StrategyConfigure routeStrategy; // = new RR(); // if no strategy
	public long pingIntervalMs = ChassisConfig.ConnectionConfig.PINGINTERVAL_MS.get();
	public boolean startWaitStable = ChassisConfig.ConnectionConfig.startWaitStable.get();
	public boolean dnsTrust = true;
	public long pingTimeoutMs = ChassisConfig.ConnectionConfig.PINGTIMEOUT_MS.get();

	public ClientConfiguration setTo3rdParty(boolean to3rdParty) {
		this.to3rdParty = to3rdParty;
		return this;
	}

	public boolean to3rdParty = false;
	//	public boolean enableServiceDiscovery = ChassisConfig.RouteConfig.ServiceDiscoveryConfig.ENABLE_SERVICEDISCOVERY.get();
//	public int maxConnectedService = ChassisConfig.RouteConfig.MAXCONNECTED_SERVICE.getInt();
	protected boolean defaultMd5 = ChassisConfig.PeerHandlerConfig.ChecksumConfig.DEFAULT_CLIENT_USE_MD5.get();
	public List<Integer> warmupTpsSteps = ChassisConfig.PerformanceConfig.WarmupConfig.WARMUP_STEPS.getInt();
	public int maxRetryConnect = ChassisConfig.ConnectionConfig.MAX_RETRYCONNECT.getInt();
	public int connectionPerIP = Integer.max(ChassisConfig.ConnectionConfig.CONNECTIONPER_IP.getInt(), 1);
	public Map<String, List<String>> mapDnsIp = new ConcurrentHashMap<>();
	public IsolationConfig isolationConfig;

	protected ClientConfiguration(String host, int port, String clientID, String lbrClientID, int maxOpenConnection, int minOpenConnection, int minConnectHost, String connectionLimitMode, StrategyConfigure routeStrategy) {
		super(clientID, host, lbrClientID, port, connectionLimitMode, minOpenConnection, maxOpenConnection);
		patchAuthenServiceName();
		this.minHost = Integer.max(minConnectHost, 1); // remove negative
		this.routeStrategy = routeStrategy;
		isolationConfig = new IsolationConfig(); // always not null
		if (defaultMd5) setMd5CheckSum();
	}

	// for TCP declare
	public ClientConfiguration(String host, int port, String clientID) {
		this(host, port, clientID, ChassisConst.STANDALONE_LBR_ID);
	}

	public ClientConfiguration(String host, int port, String clientID, String lbrId) {
		this(host, port, clientID, lbrId, 0, 1, 1, "fullmesh", new RR()); // 0 mean no limits
	}

	public ClientConfiguration(String clientID) { // for local instance, however anyone can use then overwrite fields
		this("localhost", 9000, clientID, ChassisConst.STANDALONE_LBR_ID);
	}

	protected ClientConfiguration() {
		this("noNameClient");
	}
//	protected Properties storedProperties = null;

	//	public ClientConfiguration(Properties properites) {
//		this(
//			properites.getProperty("host"),
//			Integer.parseInt(properites.getProperty("port")),
//			properites.getProperty("id"),
//			properites.getProperty("lbrId", LBRAdapter.STANDALONE_LBR_ID)
//		);
//		storedProperties = properites;
//		channelConfiguration = new ChannelConfiguration(properites);
//	}


	public IsolationConfig getIsolationConfig() {
		return isolationConfig;
	}

	public ClientConfiguration setIsolationConfig(IsolationConfig isolationConfig) {
		this.isolationConfig = Objects.requireNonNull(isolationConfig);
		return this;
	}

	public ClientConfiguration setPingIntervalMs(int pingIntervalMs) {
		this.pingIntervalMs = pingIntervalMs;
		return this;
	}

	public ClientConfiguration setSendTimeoutMs(long sendTimeoutMs) {
		this.sendTimeoutMs = sendTimeoutMs;
		return this;
	}

	@Override
	public ClientConfiguration setChannelConfiguration(ChannelConfiguration channelConfiguration) {
		this.channelConfiguration = channelConfiguration;
		return this;
	}



	public List<Integer> getWarmupTpsSteps() {
		return warmupTpsSteps;
	}

	public ClientConfiguration setWarmupTpsSteps(String warmupTpsSteps) {
		this.warmupTpsSteps = StringUtils.stringToListInt(warmupTpsSteps);
		return this;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public boolean diff(PeerConfig obj) {
		if (!(obj instanceof ClientConfiguration)) return true;
		ClientConfiguration o = (ClientConfiguration) obj;
		return o.minHost != minHost
			|| o.pingIntervalMs != pingIntervalMs
			|| o.pingTimeoutMs != pingTimeoutMs
			|| o.defaultMd5 != defaultMd5
			|| o.maxRetryConnect != maxRetryConnect
			|| o.connectionPerIP != connectionPerIP
			|| !Objects.equals(o.host, host)
			|| !Objects.equals(o.warmupTpsSteps, warmupTpsSteps)
			|| !Objects.equals(o.mapDnsIp, mapDnsIp)
			|| !Objects.equals(o.routeStrategy, routeStrategy)
			|| !Objects.equals(o.isolationConfig, isolationConfig)
			;
	}
	@Override
	public String getProtocolName() {
		return ChassisConst.SupportVersion.TCP;
	}
	public String getProtocolShortName() {
		return ChassisConst.SupportVersion.TCP_SHORT;
	}

	public void patchAuthenServiceName() {
		if(authenServiceName == null || authenServiceName.isEmpty()) authenServiceName = host;
	}
}
