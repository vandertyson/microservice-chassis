package com.viettel.vocs.microchassis.connection.loadbalancing.configure;

public class BCC extends BackConfigure {
	public int ccUpperPct = 90;
	public int ccLowerPct = 30;
	// ================ config .yml above ===============
	@Override
	public boolean diff(StrategyConfigure obj) {
		if (super.diff(obj)) return true;
		if (!(obj instanceof BCC)) return true;
		BCC o = (BCC) obj;
		return ccUpperPct != o.ccUpperPct
			|| ccLowerPct != o.ccLowerPct
			;
	}
	public String report() {
		return String.format("%d/%dCC%dHL%b@%d%%",
			ccLowerPct, ccUpperPct,
			highload, controlWaitOnHighLoadOnly, enableControlAtLoadPct);
	}
}
