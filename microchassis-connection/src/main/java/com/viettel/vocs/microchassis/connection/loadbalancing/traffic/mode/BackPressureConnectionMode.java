package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

import com.viettel.vocs.common.log.limiter.SemiAutoSlideRateLimiter;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.BackConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerConnectionState;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class BackPressureConnectionMode
	extends DynamicFrontPressureConnectionMode {
	public final AtomicInteger peerStrategyFailStack = new AtomicInteger(0);
	public final SemiAutoSlideRateLimiter peerStratFailRater = new SemiAutoSlideRateLimiter(10);
	public abstract boolean isUnderPressure();
	/**
	 * higher is better, reverse of utilization(), score is realtime
	 */
	public float score() { // always > 0, => if select conn with score = 0-> highest priority
		float u = Float.max(0.0f, Float.min(pressureSource.utilization(state.createSnapshot(getBackConfigure(), pressureSource)), 1)); // normalize u in bound (included) 0->1
		return u == 0.0f ? Float.MAX_VALUE : 1.0f / u; // -> score in range of 1->max Float
	}


	protected final Pressure pressureSource;
	public final PeerConnectionState state; // must be set on constructor



	final boolean controlWaitOnHighLoadOnly;
	protected <P extends Pressure> BackPressureConnectionMode(P pressureSource) {
		super(pressureSource.newBackConfig(), pressureSource.peerLoadBase10k); // backpressure only use maxTPS env value as init value
		this.state = ConnectionMode.newState(config);
		this.pressureSource = pressureSource;
		controlWaitOnHighLoadOnly = this.pressureSource.getConfig().controlWaitOnHighLoadOnly;
	}




	@Override
	public int getTps() {  // ban chat cua BackPressure la limit TPS tuc thoi, dua tren cac metric cau thanh nhu TPS, CC, InSQ, sLoad, ...
		// getTps duoc goi nhieu nhat trong NanoRateLimiter refresh token, -> can debounce sence()
		pressureSource.pressWithCache(getBackConfigure(), state); // use TPS method of FrontPressure
		/**
		 * ham sence() duoc goi tai thoi diem req moi waitCC
		 * -> vi du connection: cc=3, tu trai sang phai la now to past
		 * CC1:___|_______|_________|__________|____|_____|____
		 * CC2:_______|___|_________|______|____|_____|________
		 * CC3:|_____|________|_________|______|__________|____
		 * new ^ request go here
		 *  => neu draw thanh cong, draw co wait, nen draw goi den getTps, goi den pressure sence() -> report tai thoi diem wait xong, tuc la 1 trong so cac CC line tren connection da duoc release
		 *  	-> avgCC monitor se check duoc voi avg = CC-1
		 *  CC slide counter.stack sau khi acquire thanh cong, tuc la sau khi 1 CC da duoc release -> CC slide = real CC luon < 1 so voi CC config
		 */
		return super.getTps();
	}

	public BackConfigure getBackConfigure() {
		return (BackConfigure) config;
	}

	@Override
	public int getConfiguredCC() { // sense reload at getTps()
		return !controlWaitOnHighLoadOnly
			|| pressureSource.isEnableControl(pressureSource.peerLoadBase10k.get()) // only act waitCC on highload
			? getBackConfigure().concurrent.get()
			: 0; // to skip waitCC
	}
}
