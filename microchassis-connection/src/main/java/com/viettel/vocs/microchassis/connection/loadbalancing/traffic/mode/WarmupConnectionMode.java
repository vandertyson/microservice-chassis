package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class WarmupConnectionMode extends StaticLimitedFrontPressureConnectionMode implements MonitorableConnectionMode {
	public WarmupConnectionMode(List<Integer> tpsSteps, long windowNs, PeerCounter counter) {
			super(tpsSteps, windowNs, counter);
			thisStepReceivedOffset = getStepper().counter.getReceived(); // init
	}

	@Override
	public boolean isUnderPressure() {
		return false; // always allow warmup
	}

	protected final AtomicInteger peerLoad = new AtomicInteger(0);
	@Override
	public int getConfiguredCC() {
		return 1;
	}
	@Override
	public void updatePeerLoad(int base10kCpuLoad) {
		peerLoad.set(base10kCpuLoad);
	}

	@Override
	public int getPeerLoad() {
		return peerLoad.get();
	}
}