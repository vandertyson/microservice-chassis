package com.viettel.vocs.microchassis.codec.handler.tcp;

import com.viettel.vocs.microchassis.connection.client.ClientConnection;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.config.IsolationConfig;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.CircuitBreakerConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.StaticLimitedFrontPressureConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerConnectionState;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.ClientLimiter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class IsolationEngine {
	private static final Logger logger = LogManager.getLogger(IsolationEngine.class);

	private final IsolationConfig config;
	private final AtomicBoolean testing = new AtomicBoolean(false); // set on specific hit check

	private final ClientLimiter limiter;
	private final ClientConfiguration clientConfig;

	public IsolationEngine(ClientConfiguration config1, ClientLimiter limiter) {
		config = config1.isolationConfig;
		clientConfig = config1;
		this.limiter = limiter;
	}

	private void onDropIsolationHit(ClientConnection conn) { // call here which mean config existed
		// TODO define this
		if (config.dropConfig.closeOnIsolate) {
			logger.info("Close connection {} by Drop isolated", conn.getMonitorID());
			conn.deregister(); // then recreate, hold at createMsg if truly down, or else continuous trigger isolation
		} else {
			// TODO define circuitBreaker reopen monitor
			logger.info("Disable connection {} by Drop isolated", conn.getMonitorID());
			conn.setEnable(false);
		}
	}

	private void onTimeoutIsolationHit(ClientConnection conn) { // call here which mean config existed
		notifyTimeoutIsolated(conn);
		if (config.timeoutConfig.closeOnIsolate) {
			logger.info("Close connection {} by Timeout isolated", conn.getMonitorID());
			conn.deregister(); // then recreate, hold at createMsg if truly down, or else continuous trigger isolation
		} else {
			// TODO define circuitBreaker reopen monitor
			logger.info("Disable connection {} by Timeout isolated", conn.getMonitorID());
			conn.setEnable(false);
		}
	}

	private static void notifyTimeoutIsolated(ClientConnection conn) {
		PeerConnectionState state = conn.getCounter().getState();
//		int avgTPS = state.getAvgSend();
//		int avgSuccess = state.getAvgSuccess();
//		Channel channel = conn.getChannel();
//		Endpoint connEndp = conn.getEndpoint();
//		Map<String, Object> mapParam = Map.ofEntries(
//			Map.entry("trigger_timestamp", TimeUtils.nowNano()),
//			Map.entry("count_sent", avgTPS),
//			Map.entry("count_success", avgSuccess),
//			Map.entry("count_timeout", avgTPS-avgSuccess)
//		);
		conn.onIsolate(String.format("Timeout isolation hit. Isolate connection id=%s, sent=%d, errR=%.2f endpoint=%s channel=%s", conn.getId(), state.getAvgSend(), state.getErrorRate(), conn.getEndpoint(), conn.getChannelString()));//, mapParam);
	}

	public boolean isIsolateCheck(ClientConnection conn) { // trigger check on every error req
		// TODO test based on RR to reveal the problems
		return timeoutIsolationCheck(conn) || dropIsolationCheck(conn);
	}


	private final AtomicReference<ConnectionMode> oldCM = new AtomicReference<>(null);
	public ConnectionMode doneTestBringBackOldMode(ClientConnection conn) {
		testing.set(true);
		logger.info("[Iso] Connection {} done isolate circuitBreaker observation", conn.getChannel());
		ConnectionMode oldMode = oldCM.get();
//		limiter.setMode(oldMode); // return for conn.limiter to set, not set in this leaf property
		oldCM.set(null);
		return oldMode;
	}

	private void setTest(ClientConnection conn, IsolationConfig.IsolateConfig isolateConfig) {
		testing.set(true);
		if(limiter.getMode() instanceof CircuitBreakerConnectionMode) return; // is set
		logger.info("[Iso] Set connection {} to isolate circuitBreaker control", conn.getChannel());
		oldCM.compareAndSet(null, limiter.getMode()); // null mean previous state is normal, not test->test
		limiter.setMode(
			new CircuitBreakerConnectionMode(
				new StaticLimitedFrontPressureConnectionMode.LadderConfigure(
					(int)Math.ceil((double)isolateConfig.monitorSpeed/config.cbConfig.nStep), config.cbConfig.nStep, isolateConfig.monitorSpeed, config.cbConfig.window, new PeerCounter(clientConfig.routeStrategy, clientConfig.sendTimeoutMs)
				)){
				@Override
				protected boolean nextStepConditionCheck() {
					boolean baseCondition = super.nextStepConditionCheck();
					int currentStepSpeed = stepper.getStep();
					PeerConnectionState state = conn.getCounter().getState();
					boolean cbResult;

					if (currentStepSpeed < isolateConfig.nonErrSpeed){
						cbResult = state.getErrorRate() == 0f; // allow lag 1 response, then successRate+1 rate >= 100%
					} else if (currentStepSpeed < isolateConfig.monitorSpeed) {
						cbResult = state.getAvgSuccess() > isolateConfig.nonErrSpeed;
					} else { // normal check
						cbResult = state.getAvgSuccess() >= isolateConfig.nonErrSpeed && state.getErrorRate() * 100 < isolateConfig.thresholdPct;
					}
					logger.info("[Iso] Connection {} {} circuitBreaker step {}TPW", conn.getChannelString(), cbResult ? "pass": "failed", currentStepSpeed);
					if(!cbResult) reset(); // cb is false -> reset step to step0 -> return baseCondition && cbResult = false, stepper wont shift step immediately
					return baseCondition && cbResult;
				}
			});
	}

	public boolean dropIsolationHit(ClientConnection conn) {
		PeerConnectionState state = conn.getCounter().getState();
		int avgSuccess = state.getAvgSuccess();
		float errorRate = state.getErrorRate();
		int err = state.getAvgError();
		int monitoringReqCount = state.getMonitoringReqCount();
		boolean isHit = config.dropConfig != null
			&& monitoringReqCount >= config.dropConfig.monitorSpeed
			&& errorRate * 100 >= config.dropConfig.thresholdPct; // isHit
		if(isHit) {
			logger.error(String.format("DropIso hit on channel %s. suc:%d/%.2f | err:%d/%.2f/%d | mon:%d/%d", conn.getChannelString(), avgSuccess, config.timeoutConfig.nonErrSpeed, err, errorRate, config.timeoutConfig.thresholdPct, monitoringReqCount,config.timeoutConfig.monitorSpeed));
			setTest(conn, config.dropConfig);
		}
		return isHit;
	}
	public boolean timeoutIsolationHit(ClientConnection conn) {
		PeerConnectionState state = conn.getCounter().getState();
		int avgSuccess = state.getAvgSuccess();
		float errorRate = state.getErrorRate();
		int err = state.getAvgError();
		int monitoringReqCount = state.getMonitoringReqCount();
		boolean isHit = config.timeoutConfig != null
			&& monitoringReqCount >= config.timeoutConfig.monitorSpeed
			&& errorRate * 100 >= config.timeoutConfig.thresholdPct; // isHit
		if(isHit) {
			logger.error(String.format("TimeoutIso hit on channel %s. suc:%d/%.2f | err:%d/%.2f/%d | mon:%d/%d", conn.getChannelString(), avgSuccess, config.timeoutConfig.nonErrSpeed, err, errorRate, config.timeoutConfig.thresholdPct, monitoringReqCount,config.timeoutConfig.monitorSpeed));
			setTest(conn, config.timeoutConfig);
		}
		return isHit;
	}

	public boolean dropIsolationCheck(ClientConnection conn) { // isolate hit
		boolean isHit = dropIsolationHit(conn);
		if (isHit) onDropIsolationHit(conn);
		return isHit;
	}

	public boolean timeoutIsolationCheck(ClientConnection conn) { // isolate hit
		boolean isHit = timeoutIsolationHit(conn);
		if (isHit) onTimeoutIsolationHit(conn);
		return isHit;
	}
//	@Override
//	public void reset() {
//		sent.set(0);
//		timeout.set(0);
//	}

}
