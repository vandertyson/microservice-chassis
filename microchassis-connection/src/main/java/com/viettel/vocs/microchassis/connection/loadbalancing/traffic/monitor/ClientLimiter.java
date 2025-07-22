package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.codec.handler.tcp.IsolationEngine;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter.ConcurrentLimiter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter.ModeRater;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.BackPressureConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;

import java.util.concurrent.TimeoutException;

/**
 * @author tiennn18
 */
public class ClientLimiter{
	/**
	 * TrafficLimiter manages traffic of 1 microchassis.connection.Connection
	 * 	intervention can deploy at
	 * 	ConnectionStrategy:
	 */
	private final ModeRater rater;


	private final ConcurrentLimiter concurrentManager;
	public int warmingUpTPS(){
		return rater.warmingUpTPS();
	}

	private PeerCounter counter;
	protected ConnectionMode mode;
	private final ClientConfiguration config;
	public final IsolationEngine isolationEngine;
	public ClientLimiter(ConnectionMode mode, ClientConfiguration config) {
		this.config = config;
		setMode(mode);
		// default 0 ~ max available, this can cause concurrent delay all
		rater = new ModeRater(this.mode);
		concurrentManager = new ConcurrentLimiter(counter);
		isolationEngine = !(config instanceof HttpClientOptions) // client not HTTP
			&& ((ClientConfiguration)config).isolationConfig.enable
			? new IsolationEngine((ClientConfiguration) config, this)
			: null;
	}
	public void close(){
		rater.close();
		mode.close();
		concurrentManager.close();
		counter.disableCount();
	}

	public <R> R accquireFor(TimeUtils.NanoBeacon startBeacon, R req) throws TimeoutException {
		acquire(startBeacon);
		return req;
	}
	public boolean tryAcquire() {
		// xin gui khong hoi lai, khong wait
		int configuredCC = mode.getConfiguredCC();
		return concurrentManager
			.setMaxConcurrent(configuredCC)
			.draw()  // ccMgr draw la check dieu kien, co the thuc hien bat ky luc nao
			&& (configuredCC == 0 || rater.tryDraw());
	}
	public void acquire(TimeUtils.NanoBeacon startBeacon) throws TimeoutException {
		int configuredCC = mode.getConfiguredCC();

		concurrentManager
			.setMaxConcurrent(configuredCC)
			.waitConcurrent(startBeacon); // only backpressure wait for draw CC, front don't
		// no need to wait for FrontPressure
		if(configuredCC > 0) rater.waitDraw(startBeacon); // if front pressure limit TPS, configCC > 0 mean enable wait  // rater draw rut ticket khong hoan lai, nen phai rut cuoi

	}

	public ConnectionMode getMode() {
		return rater.getMode();
	}
	public ConnectionMode setMode(ConnectionMode newMode) { // -> re-set counter
		mode = newMode;
		counter = newMode instanceof BackPressureConnectionMode
			? new PeerCounter(((BackPressureConnectionMode) newMode).state, config.sendTimeoutMs)
			: new PeerCounter(config.routeStrategy, config.sendTimeoutMs); // to store at Connection in case of channel down attr null
		counter.getState().setWindow(mode.getWindow()); // at init dont have mode
		return mode;
	}

	public ConnectionMode nextMode(ConnectionMode newMode) { // TODO for future, if routestrategy has multiple mode on 1 conn
			return setMode(newMode);
	}

	public PeerCounter getCounter() {
		return counter;
	}
}
