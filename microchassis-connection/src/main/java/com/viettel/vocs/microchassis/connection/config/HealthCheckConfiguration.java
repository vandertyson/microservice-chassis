package com.viettel.vocs.microchassis.connection.config;

import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.common.config.loader.ConfigLoader;

import java.util.Objects;


public class HealthCheckConfiguration extends ConfigLoader<HealthCheckConfiguration> {
	public int healthCheckIntervalSec = ChassisConfig.HealthcheckConfig.HTTP_HEALTHCHECKINTERVAL_SEC.getInt();
	public int healthCheckTimeoutMs = ChassisConfig.HealthcheckConfig.HTTP_HEALTHCHECKTIMEOUT_MS.getInt();
	public int healthCheckMaxRetry = ChassisConfig.HealthcheckConfig.HTTP_HEALTHCHECKMAX_RETRY.getInt();
	public String path = ChassisConfig.ManoConfig.HEALTH_CHECK_PATH.get();

	@Override
	public boolean diff(HealthCheckConfiguration obj) {
		return obj == null
			|| !Objects.equals(obj.path, path)
			|| obj.healthCheckIntervalSec != healthCheckIntervalSec
			|| obj.healthCheckTimeoutMs != healthCheckTimeoutMs
			|| obj.healthCheckMaxRetry != healthCheckMaxRetry
			;
	}

	public HealthCheckConfiguration() {
	}

	public HealthCheckConfiguration setHealthCheckIntervalSec(int healthCheckIntervalSec) {
		this.healthCheckIntervalSec = healthCheckIntervalSec;
		return this;
	}

	public HealthCheckConfiguration setHealthCheckTimeoutMs(int healthCheckTimeoutMs) {
		this.healthCheckTimeoutMs = healthCheckTimeoutMs;
		return this;
	}

	public HealthCheckConfiguration setHealthCheckMaxRetry(int healthCheckMaxRetry) {
		this.healthCheckMaxRetry = healthCheckMaxRetry;
		return this;
	}

	public HealthCheckConfiguration setPath(String path) {
		this.path = path;
		return this;
	}
}

