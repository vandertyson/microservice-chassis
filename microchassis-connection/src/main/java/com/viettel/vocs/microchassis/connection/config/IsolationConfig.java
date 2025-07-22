package com.viettel.vocs.microchassis.connection.config;

import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.common.config.loader.ConfigLoader;

import java.util.Objects;

public class IsolationConfig extends ConfigLoader<IsolationConfig> {
	public boolean enable = ChassisConfig.ConnectionConfig.CircuitBreakerConfig.ISOLATE_ENABLE.get();
	public DropConfig dropConfig;
	public TimeoutConfig timeoutConfig;
	public CircuitBreakerConfig cbConfig;

	public IsolationConfig() {
		dropConfig = new DropConfig();
		timeoutConfig = new TimeoutConfig();
		cbConfig = new CircuitBreakerConfig();
	}

	@Override
	public boolean diff(IsolationConfig obj) {
		return obj == null
			|| !Objects.equals(obj.dropConfig, dropConfig)
			|| !Objects.equals(obj.timeoutConfig, timeoutConfig)
			|| !Objects.equals(obj.cbConfig, cbConfig)
			;
	}

	public static class IsolateConfig extends ConfigLoader<IsolateConfig> {
		public boolean closeOnIsolate = ChassisConfig.ConnectionConfig.CircuitBreakerConfig.ISOLATION_BEHAVIOR_ONISOLATE_CLOSE.get();
		public int monitorSpeed = ChassisConfig.ConnectionConfig.CircuitBreakerConfig.ISOLATION_MONITORSPEED.getInt();
		public int thresholdPct = ChassisConfig.ConnectionConfig.CircuitBreakerConfig.ISOLATION_THRESHOLDPERCENT.getInt();
		public float nonErrSpeed = thresholdPct * monitorSpeed / 100f;

		@Override
		public boolean diff(IsolateConfig obj) {
			return obj == null
				|| closeOnIsolate != obj.closeOnIsolate
				|| monitorSpeed != obj.monitorSpeed
				|| thresholdPct != obj.thresholdPct
				|| nonErrSpeed != obj.nonErrSpeed
				;
		}

		// CB test: when re enable again
		//  under nonErrSpeed: required 0% error to increase or else reset test, nhung tai vi co lag, nen duoc phep lag toi da -1
		//  among nonErrSpeed under monitorSpeed: required nonErrSpeed to increase or else reset test
		//  among monitorSpeed: to normal run or else reset test
		//      normal run: required curMonitorSpeed>monitorSpeed && err<thresholdRate // or else isolate and CB test
		//  but speed of success shift with avgTime from sent speed, timeout speed shift by timeoutDuration 5000 >> window or interval 1000 -> too lag, if timeout sent before, then reduce send speed -> hit isolate easily
		// => monitor on timeoutRate or dropRate directly over curMonitorSpeed

		public float getNonErrSpeed() {
			return nonErrSpeed;
		}

		public IsolateConfig setNonErrSpeed(float nonErrSpeed) {
			this.nonErrSpeed = nonErrSpeed;
			return this;
		}

		public boolean isCloseOnIsolate() {
			return closeOnIsolate;
		}

		public IsolateConfig setCloseOnIsolate(boolean closeOnIsolate) {
			this.closeOnIsolate = closeOnIsolate;
			return this;
		}

		public int getThresholdPct() {
			return thresholdPct;
		}

		public IsolateConfig setThresholdPct(int thresholdPct) {
			this.thresholdPct = thresholdPct;
			return this;
		}
	}

	public static class TimeoutConfig extends IsolateConfig {
	}

	public static class DropConfig extends IsolateConfig {
	}

}
