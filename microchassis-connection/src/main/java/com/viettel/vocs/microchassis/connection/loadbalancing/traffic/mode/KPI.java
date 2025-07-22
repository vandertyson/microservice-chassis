package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

import com.viettel.vocs.microchassis.connection.exception.StrategyException;

/**
 * @author tiennn18
 */
public class KPI implements APIKPI{
	float upRate = 0.05f;
	float keepRate = 0.1f;
	float downRate = 0.5f;
	public KPI(String name, int outOfKPIms, float upRate, float keepRate, float downRate) throws StrategyException {
		this(name, outOfKPIms);
		if(0 < upRate && upRate <= keepRate && keepRate <= downRate && downRate <= 1f) {
			this.upRate = upRate;
			this.keepRate = keepRate;
			this.downRate = downRate; // resetRate is the rest
		} else {
			throw new StrategyException(null, String.format("KPI illegal: [%f:%f:%f:%f]", upRate, keepRate, downRate, 1-downRate));
		}
	}
	public KPI(String name, int outOfKPIms, float upRate, float keepRate) throws StrategyException {
		this(name, outOfKPIms, upRate, keepRate, 1f); // resetRate not reach
	}
	public KPI(String name, int outOfKPIms){
	}
}
