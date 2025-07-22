package com.viettel.vocs.microchassis.connection.loadbalancing.configure;

import com.viettel.vocs.common.os.RateClock;
import com.viettel.vocs.microchassis.base.ChassisConfig;

import java.util.concurrent.TimeUnit;

public class RPL extends RR {
	/**
	 * connectionPerIp is pool size
	 */
	public long maxWaitCcNs = TimeUnit.MILLISECONDS.toNanos(ChassisConfig.ConnectionConfig.Http1Config.HTTP1_TIMEOUT_MS.get());
	public long select1TimeoutNs = 1000 * RateClock.TICK;
	public long maxWait1SelectNs = maxWaitCcNs;

	@Override
	public boolean diff(StrategyConfigure obj) {
		if (super.diff(obj)) return true;
		if (!(obj instanceof RPL)) return true;
		RPL o = (RPL) obj;
		return maxWaitCcNs != o.maxWaitCcNs
			|| select1TimeoutNs != o.select1TimeoutNs
			|| maxWait1SelectNs != o.maxWait1SelectNs
			;
	}
}
