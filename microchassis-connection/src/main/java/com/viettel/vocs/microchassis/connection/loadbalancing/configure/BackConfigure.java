package com.viettel.vocs.microchassis.connection.loadbalancing.configure;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author tiennn18
 */
public class BackConfigure extends FrontConfigure {
	public int ccUpperPct = 90;
	public int ccLowerPct = 30;
	public int tpsStep = 10; // min instance 2 core nhu CGW load 3500TPS -> de len max trong 1s can init 60 stateChanges/s -> de len trong 12s can 5tps
	public int ccStep = 1;
	public int lowestCcUtilizationPct = 5;
	public int initCC = Integer.max(ccStep, 100/lowestCcUtilizationPct); // < 1req * 100 / lowestCcUtilizationPct to not trigger cut down at first boot
	public boolean controlWaitOnHighLoadOnly = true;
	public int debounceDurationMs = 100; // if out system shock into OCS, time to response to cut down or change state is at least 100ms
	public int enableControlAtLoadPct = 60;
	// ================ config .yml above ===============
	@Override
	public boolean diff(StrategyConfigure obj) {
		if (super.diff(obj)) return true;
		if (!(obj instanceof BackConfigure)) return true;
		BackConfigure o = (BackConfigure) obj;
		return ccUpperPct != o.ccUpperPct
			|| ccLowerPct != o.ccLowerPct
			|| tpsStep != o.tpsStep
			|| lowestCcUtilizationPct != o.lowestCcUtilizationPct
			|| ccStep != o.ccStep
			|| initCC != o.initCC
			|| debounceDurationMs != o.debounceDurationMs
			|| enableControlAtLoadPct != o.enableControlAtLoadPct
			|| controlWaitOnHighLoadOnly != o.controlWaitOnHighLoadOnly;
	}

	public final AtomicInteger concurrent = new AtomicInteger(2); // toi thieu 2 de chay async
	public BackConfigure() {
		super();
	}

	public BackConfigure(int initTPS, int initCC) {
		super(initTPS);
		concurrent.set(Math.max(initCC, concurrent.get()));
	}
	public void changeConcurrent(int cc) {
		if (cc > 1) concurrent.set(cc);
	}
}
