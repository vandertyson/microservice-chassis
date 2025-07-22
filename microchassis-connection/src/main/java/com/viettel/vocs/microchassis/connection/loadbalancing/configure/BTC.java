package com.viettel.vocs.microchassis.connection.loadbalancing.configure;

public class BTC extends BackConfigure {
	public int tpsUpperPct = 90;
	public int tpsLowerPct = 60;
	public int lowestTpsUtilizationPct = 10;
	public int initTPS = Integer.max(tpsStep, 100 / lowestTpsUtilizationPct); // at least > 1req * 100 / lowestTpsUtilizationPct
	// ================ config .yml above ===============
	public String report() {
		return String.format("%d/%dCC%d/%dTPS%dHL%b@%d%%",
			ccLowerPct, ccUpperPct,
			tpsLowerPct, tpsUpperPct,
			highload, controlWaitOnHighLoadOnly, enableControlAtLoadPct);
	}
	@Override
	public boolean diff(StrategyConfigure obj) {
		if (super.diff(obj)) return true;
		if (!(obj instanceof BTC)) return true;
		BTC o = (BTC) obj;
		return tpsUpperPct != o.tpsUpperPct
			|| tpsLowerPct != o.tpsLowerPct
			|| lowestTpsUtilizationPct != o.lowestTpsUtilizationPct
			|| initTPS != o.initTPS
			;
	}
}
