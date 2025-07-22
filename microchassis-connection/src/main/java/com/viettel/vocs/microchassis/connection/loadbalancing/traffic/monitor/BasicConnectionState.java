package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor;

import com.viettel.vocs.microchassis.connection.loadbalancing.configure.BackConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnState;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.Pressure;
import com.viettel.vocs.common.log.slideMonitor.CountSlideMonitor;

public abstract class BasicConnectionState implements ConnState {
	protected final CountSlideMonitor avgSuccess = new CountSlideMonitor();
	protected final CountSlideMonitor avgError = new CountSlideMonitor();
	protected final CountSlideMonitor avgSend = new CountSlideMonitor();

	@Override
	public void setWindow(long window) {
		// do nothing for CC window
		avgSend.setWindow(window);
		avgSuccess.setWindow(window);
		avgError.setWindow(window);
	}
	@Override
	public void stackSuccess() {
		avgSuccess.stack();
	}
	@Override
	public void stackError() {
		avgError.stack();
	}

	@Override
	public void stack() {
		// do nothing for stack CC
		avgSend.stack();
	}
	@Override
	public int getMonitoringReqCount() {
		return getAvgSuccess() + getAvgError();
	}
	@Override
	public int getAvgError() {
		return avgError.get();
	}
	@Override
	public int getAvgSend() {
		return avgSend.get();
	}

	@Override
	public int getAvgSuccess() {
		return avgSuccess.get();
	}

	public StateSnapshot createSnapshot(BackConfigure conf, Pressure pressureSource) {
		return new StateSnapshot(conf, this, pressureSource);
	}
}
