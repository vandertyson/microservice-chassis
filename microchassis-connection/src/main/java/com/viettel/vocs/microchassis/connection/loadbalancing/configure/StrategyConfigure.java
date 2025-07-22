package com.viettel.vocs.microchassis.connection.loadbalancing.configure;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.config.loader.ConfigLoader;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author tiennn18
 */
public abstract class StrategyConfigure extends ConfigLoader<StrategyConfigure> {
	/**
	 * hold config and compose JIT value for RateLimiter, ConnMode, Strategies
	 */
	public String logId = "rs";
	public String clientId = "client"; // set by ServiceTemplate
	/**
	 * for update config
	 */
	public boolean enableWarmup = false;
	public int maxConnectionPerIp = 0; // set to 0 or less than connectionPerIp to use static create amount = connectionPerIp
	public int highload = 80; // BTC 85 CC 90 LLC 80
	public int ccDurationMs = 0; // target 1000ms kpi ocs // threshold of time that consider a sent request is normal to calc into CC or a timeout/abnormal request to be ignore from routing param
	// ================ config .yml above ===============

	@Override
	public boolean diff(StrategyConfigure obj) {
		return obj == null
			|| !Objects.equals(logId, obj.logId)
			|| !Objects.equals(clientId, obj.clientId)
			|| obj.enableWarmup != enableWarmup
			|| obj.maxConnectionPerIp != maxConnectionPerIp
			|| obj.highload != highload
			|| obj.ccDurationMs != ccDurationMs
			;
	}

	public abstract long getMinWait();
	public abstract int getTPS();
	public abstract long getWindow(); // window ns
	public abstract boolean changeTPS(int newTps);
	// TODO check hieu ung khi doi TPS qua nhanh se dan den don queue gay chet cuc bo
	public abstract boolean changeTPS(int newTps, int newShockTps);
	protected final AtomicLong tpsWindow = new AtomicLong(CommonConfig.RateLimiterConfig.DEFAULT_WINDOW.get());
	public boolean changeWindow(long newWindow) {
		if(newWindow > 0) {
			tpsWindow.set(newWindow);
			return true;
		}
		return false;
	}
}
