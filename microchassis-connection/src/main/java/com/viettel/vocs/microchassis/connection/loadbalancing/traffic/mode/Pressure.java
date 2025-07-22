package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

import com.viettel.vocs.microchassis.connection.loadbalancing.configure.BackConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerConnectionState;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.StateSnapshot;
import com.viettel.vocs.common.os.TimeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public abstract class Pressure implements PressureLayer {
	protected static final Logger logger = LogManager.getLogger(Pressure.class);
	protected final AtomicLong lastLog = new AtomicLong(TimeUtils.nowNano());
	protected final String channelId;
	public static final AtomicReference<String> lastChannelId = new AtomicReference<>();
	protected float LOWEST_CC_UTILIZATION; // = 0.01f; // maintain operate metric at rate at least 1% ~ 1 avgCC for max 100CC configured, if avgCC =0 -> skip this seal
	protected int PEER_HIGHLOAD_THRESHOLD; //  = 90_00; // chot nguong 90% tot nhat
	protected int ENABLE_CONTROL_THRESHOLD; //  = 90_00; // chot nguong 90% tot nhat
	protected final int DEBOUNCE_DURATION_MS;
	public abstract <Conf extends BackConfigure> Conf getConfig();
	public boolean estimateLoadStatelessOnline() {
		// khong phan biet duoc cao tai hay thap tai bang
		/**
		 * base 10k mean 80%, 2 core ~ 160%,
		 * ver2 set 90% vi cpu monitor tinh tong cpu cua ca pod, 2 core ~ 180%
		 */
		return peerLoadBase10k.get() >= PEER_HIGHLOAD_THRESHOLD;
	}
	/**
	 * lower is better
	 *
	 * @return health metrics, on scale of 100%, higher more likely to be throttle
	 */
	public abstract float utilization(StateSnapshot snapshot);
	private final AtomicInteger pressable = new AtomicInteger(1);

	boolean debounceCC(float miliDurationFromLastChangeCC) {
		/**
		 * CPU update debounce at rate 100TPS~10ms
		 * binh thuong thay chay top update sau 200ms 1 lan, vay 1 update anh huong toi 4 nhay
		 *
		 * this debounce time need to > than cpu debounce rate ms
		 */
		return miliDurationFromLastChangeCC >= DEBOUNCE_DURATION_MS; // 3TPS
	}

	boolean debounceTPS(float miliDurationFromLastChangeTPS) {
		return miliDurationFromLastChangeTPS >= DEBOUNCE_DURATION_MS; // 20TPS
	}

	public final AtomicInteger peerLoadBase10k; // load is stored at the end of tail

	@Override
	public void pressWithCache(BackConfigure current, PeerConnectionState state) {
		if (pressable.decrementAndGet() == 0) { // neu dang khong press thi return true va set isPressing
			press(current, state);
			pressable.set(1); // set false thi msg tiep theo moi press duoc
		}
	}

	/**
	 * @param tpsStep: realtime press cho moi request thi co the set step = 100, vi CC start bang 2,
	 * nen se luon co 1 msg tra ve, voi k tps, se echo duoc k-1 lan update trong 1s dau tien
	 * -> neu roi vao truong hop tac nghen -> co the tang lien tuc
	 * (k-1)lan * k tpsStep = k^2-k TPS diff/s (9900 voi 100tpStep)
	 * Netty peak 100kTPS, tai day thuc te voi FrontPressure RR va LCC client dat 40kTPS
	 * -> neu muon gioi han de 1 service len max can 5s thi toi da 1s diff = 8kTPS => step=90
	 */
	public final int tpsStep;// = 90;
	public final int ccStep;// = 1;
	// Khong implement press() o day, de cho noi nao dinh nghia Pressure se tu dinh nghia lai
	public final Pressure nextPressure;

	public final void pressNext(BackConfigure lastPressure, PeerConnectionState state) {
		if (nextPressure != null) nextPressure.pressWithCache(lastPressure, state);
	}

	protected Pressure(String channelId, BackConfigure conf) {
		this(channelId, null, conf);
	}

	protected Pressure(String channelId, Pressure nextPressure, BackConfigure conf) {
		this.channelId = channelId;
		this.nextPressure = nextPressure;
		peerLoadBase10k = nextPressure != null ? nextPressure.peerLoadBase10k : new AtomicInteger(0);
		LOWEST_CC_UTILIZATION = conf.lowestCcUtilizationPct / 100f;
		DEBOUNCE_DURATION_MS = conf.debounceDurationMs;
		ENABLE_CONTROL_THRESHOLD = conf.enableControlAtLoadPct * 100;
		tpsStep = conf.tpsStep;
		ccStep = conf.ccStep;
	}

	public abstract BackConfigure newBackConfig();

	public boolean isEnableControl(int peerLoadBase10k) {
		return peerLoadBase10k >= ENABLE_CONTROL_THRESHOLD;
	}

	public abstract String report(BackConfigure current, PeerConnectionState state);
}