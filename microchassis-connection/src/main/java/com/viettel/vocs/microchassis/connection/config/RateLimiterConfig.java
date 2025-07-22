package com.viettel.vocs.microchassis.connection.config;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.config.loader.ConfigLoader;

import java.util.concurrent.TimeUnit;

/**
 * @author vttek
 */
public class RateLimiterConfig extends ConfigLoader<RateLimiterConfig> {

	public int maxConcurrent = 0; // default 0 no limit
	public int maxTps = 0; // default 0 no limit
	public long lockCycleNs = CommonConfig.RateLimiterConfig.DEFAULT_PARK_NANO_TIME.get();
	public long lockTimeoutNs = TimeUnit.MILLISECONDS.toNanos(CommonConfig.RateLimiterConfig.DEFAULT_MAX_LOCK_TIME_MS.get());

	@Override
	public boolean diff(RateLimiterConfig obj) {
		return obj == null
			|| obj.maxConcurrent != maxConcurrent
			|| obj.maxTps != maxTps
			|| obj.lockCycleNs != lockCycleNs
			|| obj.lockTimeoutNs != lockTimeoutNs
			;
	}

	public RateLimiterConfig setMaxConcurrent(int maxConcurrent) {
		this.maxConcurrent = maxConcurrent;
		return this;
	}

	public RateLimiterConfig setLockCycleNs(long lockCycleNs) {
		this.lockCycleNs = lockCycleNs;
		return this;
	}

	public RateLimiterConfig setMaxLockTimeMs(long maxLockTimeMs) {
		this.lockTimeoutNs = TimeUnit.MILLISECONDS.toNanos(maxLockTimeMs);
		return this;
	}
}
