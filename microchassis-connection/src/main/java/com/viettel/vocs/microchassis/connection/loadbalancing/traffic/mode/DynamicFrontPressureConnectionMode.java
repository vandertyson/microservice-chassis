package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

import com.viettel.vocs.microchassis.connection.loadbalancing.configure.FrontConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * flat config front pressure not from static generated rules, which mean throughput config is determined JIT
 */
public abstract class DynamicFrontPressureConnectionMode
	implements MonitorableConnectionMode {

	protected final FrontConfigure config;
	protected final AtomicInteger peerLoad;

	protected DynamicFrontPressureConnectionMode() {
		this(new FrontConfigure());
	}

	protected DynamicFrontPressureConnectionMode(FrontConfigure config) {
		this.config = config;
		peerLoad = new AtomicInteger();
	}

	protected DynamicFrontPressureConnectionMode(FrontConfigure config, AtomicInteger peerLoadAtomicLong) {
		this.config = config;
		peerLoad = peerLoadAtomicLong; // ~>source ~> source ~> tail contain peerLoad
	}

	@Override
	public boolean isEnd() {
		return false;
	}

	@Override
	public void close() { // overwrite
		config.operateMaxAvgTPS.set(0);
		config.operateMaxPeakTPS.set(0);
	}


	@Override
	public StrategyConfigure getConfig() {
		return config;
	}

	@Override
	public long getWindow() {
		return config.getWindow();
	}

	@Override
	public long getMinWait() {
		return config.getMinWait();
	}

	@Override
	public int getTps() {
		return config.getTPS();
	}

	@Override
	public int getConfiguredCC() {
		return 0;
	}

	public void updatePeerLoad(int base10kCpuLoad) {
		peerLoad.set(base10kCpuLoad);
	}

	@Override
	public int getPeerLoad() {
		return peerLoad.get();
	}
}
