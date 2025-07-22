package com.viettel.vocs.microchassis.connection.loadbalancing.configure;

import java.util.concurrent.atomic.AtomicInteger;

public class FrontConfigure extends StrategyConfigure {
	public int maxPeakTPS = 400000; // Integer.getInteger("maxPeakTPS", 400000); // 400k la so an toan da test
	public int maxAvgTPS = 100000; // Integer.getInteger("maxAvgTPS", 100000); // maxAllowTPS < 10*maxTPS -> maxTPS < 40k
	public int selectTimeoutNs = 100000; // 100 micro per select
	// ================ config .yml above ===============


	//	Last full luc 813229512
	//	Empty luc 927397187 tuc la sau 114ms thi shock het 1000 ticket
	//  tuong duong 8771 ticket consume trong 1 giay * payloadSize 4KB
	public final AtomicInteger operateMaxPeakTPS = new AtomicInteger(maxPeakTPS);
	public final AtomicInteger operateMaxAvgTPS = new AtomicInteger(maxAvgTPS);
	@Override
	public boolean diff(StrategyConfigure obj) {
		if (super.diff(obj)) return true;
		if (!(obj instanceof FrontConfigure)) return true;
		FrontConfigure o = (FrontConfigure) obj;
		return selectTimeoutNs != o.selectTimeoutNs
			|| maxAvgTPS != o.maxAvgTPS
			|| maxPeakTPS != o.maxPeakTPS;
	}

	@Override
	public long getMinWait() {
		return (operateMaxPeakTPS.get() > 0)
			? tpsWindow.get() / operateMaxPeakTPS.get()
			: Long.MAX_VALUE;
	}

	@Override
	public int getTPS() {
		return operateMaxAvgTPS.get();
	}

	@Override
	public long getWindow() {
		return tpsWindow.get();
	}

	@Override
	public boolean changeTPS(int newTps) {
		return changeTPS(newTps, Integer.min(10 * operateMaxAvgTPS.get(), maxPeakTPS)); // curTps = 0 -> hardTps=0 -> set failed
	}

	@Override
	public boolean changeTPS(int newTps, int newShockTps) {
		if (
			newTps <= maxPeakTPS
				&& newShockTps >= newTps && newTps > 0
		) {
			operateMaxPeakTPS.set(newShockTps);
			operateMaxAvgTPS.set(newTps);
			return true;
		}
		return false;
	}



	public FrontConfigure(int initTPS) { // co the set tps toi maxTPS, maxAllowTPS
		this();
		maxAvgTPS=maxPeakTPS;
		changeTPS(initTPS > 0 ? initTPS : maxAvgTPS); // init < 10*init < maxAllowTPS <= 400k
	}

	public FrontConfigure(int initTPS, int initAllowTPS) { // co the set tps toi maxTPS, maxAllowTPS
		this();
		maxPeakTPS = Integer.min(maxPeakTPS, initAllowTPS);
		maxAvgTPS = maxPeakTPS;
		changeTPS(initTPS > 0 ? initTPS : maxAvgTPS); // init < 10*init < maxAllowTPS <= 400k
	}

	public FrontConfigure() { // default chi co the set tps toi maxTPS = 1/10 maxAllowTPS
		// default
		// fill max values if user define =0
		if (maxAvgTPS == 0) maxAvgTPS = 100000; // 128B PAT01 server handle 80kTPS server push 34kTPS/40kTPS default
		if (maxPeakTPS == 0) maxPeakTPS = 400000;
		changeTPS(maxAvgTPS, maxPeakTPS); // 100k 400k
	}
}
