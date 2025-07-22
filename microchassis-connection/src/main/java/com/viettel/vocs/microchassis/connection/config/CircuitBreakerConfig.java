package com.viettel.vocs.microchassis.connection.config;

import com.viettel.vocs.common.CommonConfig;

/**
 * @author tiennn18
 */
public class CircuitBreakerConfig {
	public int nStep = 10;
	public long window = CommonConfig.RateLimiterConfig.DEFAULT_WINDOW.get();
}
