package com.viettel.vocs.microchassis.connection.config;

import com.viettel.vocs.common.config.loader.ConfigLoader;
import com.viettel.vocs.common.hashing.CheckSumEngine;
import com.viettel.vocs.common.hashing.CheckSumEngineContainer;
import com.viettel.vocs.common.hashing.MD5CheckSumEngine;
import com.viettel.vocs.microchassis.base.ChassisConfig;

public abstract class PeerConfig extends ConfigLoader<PeerConfig> implements CheckSumEngineContainer {
	public final String id;
	public final int port;
	public String host; // server set null for bind 0.0.0.0
	public final String lbrId;
	public long gracefulShutdownMs = ChassisConfig.ConnectionConfig.DEFAULT_GRACE_TIMEOUT_MS.get();
	public final int minConnection;
	public final int maxConnection;
	public long sendTimeoutMs = ChassisConfig.ConnectionConfig.DEFAULT_SEND_TIMEOUT_MS.get();
	public boolean decomposeAfterHandle = ChassisConfig.PeerHandlerConfig.DECOMPOSECTX_ON_DONE.get();
	public boolean setAutoDecompose(boolean isEnable){
		return decomposeAfterHandle = isEnable;
	}
	public ChannelConfiguration channelConfiguration = new ChannelConfiguration();
	public PeerConfig setChannelConfiguration(ChannelConfiguration channelConfiguration) {
		this.channelConfiguration = channelConfiguration;
		return this;
	}

	public void setMd5CheckSum() {
		if(checkSumEngine == null || !(checkSumEngine instanceof MD5CheckSumEngine)) setCheckSumEngine(MD5CheckSumEngine.getInstance());
	}
	protected CheckSumEngine checkSumEngine = null;
	protected CheckSumEngine backedUpCheckSumEngine = null;

	@Override
	public synchronized void enableCheckSum() {
		checkSumEngine = backedUpCheckSumEngine;
	}

	@Override
	public synchronized void disableCheckSum() {
		backedUpCheckSumEngine = checkSumEngine;
		checkSumEngine = null;
	}
	@Override
	public void setCheckSumEngine(CheckSumEngine engine) {
		if(checkSumEngine == null || !engine.getClass().equals(checkSumEngine.getClass()))
			backedUpCheckSumEngine = checkSumEngine = engine;
	}

	public CheckSumEngine getCheckSumEngine() {
		return checkSumEngine;
	}

	public static final String FULL_MESH_MODE ="fullmesh";
	public String connectionLimitMode = FULL_MESH_MODE;
	protected PeerConfig(String id, String host, String lbrId, int port, String limitConnectionMode, int minOpenConnection, int maxOpenConnection) {
		this.id = id;
		this.host = host;
		this.lbrId = lbrId;
		this.port = port;
		this.connectionLimitMode = limitConnectionMode;
		this.maxConnection = maxOpenConnection > 0 ? maxOpenConnection : Integer.MAX_VALUE; // remove negative
		this.minConnection = Integer.max(Integer.min(minOpenConnection, this.maxConnection), 1); // remove negative, max bias
		if(ChassisConfig.PeerHandlerConfig.ChecksumConfig.DEFAULT_IS_ENABLEMD5.get()) setMd5CheckSum(); // set with default
	}

	public abstract String getProtocolName();
	public abstract String getProtocolShortName();
}
