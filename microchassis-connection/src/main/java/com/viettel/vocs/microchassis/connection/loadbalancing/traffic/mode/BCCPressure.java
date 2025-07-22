package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

import com.viettel.vocs.microchassis.connection.loadbalancing.configure.BCC;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.BackConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.StateSnapshot;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerConnectionState;
import com.viettel.vocs.common.os.TimeUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author tiennn18
 * FSM QoS xem drawio page State QoS, rut gon tu TPSCC
 * Can phan biet thoi diem request den nhieu va thoi diem ranh roi he thong
 * * 		Khi thay doi chi thay doi concurrent CC
 * * 	 		TH1: avgConcurrent > 95% ->
 * *					long run api in flight, incoming at client low
 * *						tai server cao -> server resource high concurrent -> can giam concurrent -> -1CC ve 1(loop)
 * *								-> avgCC cang cao + stop send -> TH1 cho den khi giam server load
 * *						tai server thap -> server co the nhan them mini msg -> +1CC
 * *					short run api in flight ->
 * *						tai server cao -> true overload -> -1CC
 * *	    	 		tai server thap -> nghen CC tai client -> +1CC => tang latency => tang inflight msg -> TPS tang -> 1 (chua du)|2
 * * 	 		TH2: avgConcurrent 70%-95% -> stable normal load, do nothing
 * *					server tai cao -> chua co nhieu msg den, server van don duoc het, neu them request se sang 1
 * * 					server tai thap -> good
 * * 			TH3: avgConcurrent < 70% -> server ranh roi
 * *					neu thua nhieu CC qua do 1 lan peak TH1 truoc do thi -> -1CC -> 2
 * *					server tai cao -> giam ngay so cc ve current CC
 * * 					server tai thap -> good (mark as TH4) config dang cao
 * <p>
 * neu khong doan duoc server load, TH1 neu +1CC loop -> tang mai -> tang tac nghen -> luon >95% -> khong tu giai quyet duoc, neu giam se ve 0
 */
public class BCCPressure extends Pressure {
	final float CC_UPPER; // = 0.9f; // 0.8f > 80% based on increaseCCthreshold = 1,2
	final float CC_LOWER ;//= 0.3f; // = 3x reduce 0.1f
	private final BCC bccConfigure;



	final float INCREASE_CC_THRESHOLD;
	/**
	 * this object ref by all connection on 1 client, highly concurrency, all writable obj must thread safe
	 */
	private final AtomicInteger lastAction = new AtomicInteger(0);
	public int getCurrentFsmState(){
		return lastAction.get();
	}
	public BCCPressure(String channelId, BCC bccConfigure) {
		super(channelId, bccConfigure);
		this.bccConfigure = bccConfigure;
		CC_UPPER = bccConfigure.ccUpperPct/100f;
		CC_LOWER = bccConfigure.ccLowerPct/100f;
		assert CC_LOWER > LOWEST_CC_UTILIZATION;
		INCREASE_CC_THRESHOLD  = (0.2f + 1) / CC_UPPER;
		PEER_HIGHLOAD_THRESHOLD = bccConfigure.highload*100;
	}

	@Override
	public BCC getConfig() {
		return bccConfigure;
	}

	@Override
	public BackConfigure newBackConfig() {
		BackConfigure backConfigure = new BackConfigure();
		backConfigure.concurrent.set(Integer.max(bccConfigure.initCC, 2));
		return backConfigure;
	}



	protected int examineCurrentState(StateSnapshot snapshot, boolean isHighLoad) {
		return (snapshot.ccUtilization >= CC_UPPER)
			? 1
			: (snapshot.ccUtilization < CC_LOWER
//				? (isHighLoad ? 3 : 4)
			&& isHighLoad ? 3
			: 2
		);
	}
	private int increaseCCbyStep(BackConfigure conf, StateSnapshot snapshot, int nStep) {
		int curCC = conf.concurrent.get();
		return curCC + (
			(float) curCC / snapshot.recentCC > INCREASE_CC_THRESHOLD
				? 0
				: ccStep * nStep
		);
	}

	private void increaseCCUtilize(BackConfigure conf, StateSnapshot snapshot) {
		if (LOWEST_CC_UTILIZATION > snapshot.ccUtilization && snapshot.recentCC > 0)
			conf.changeConcurrent(Math.max(conf.concurrent.get() - ccStep, ccStep));
	}

	protected final AtomicLong lastChangeCCbyLoad = new AtomicLong(0);

	@Override
	public float utilization(StateSnapshot snapshot) {
		return snapshot.ccUtilization;
	}

	@Override
	public void press(BackConfigure conf, PeerConnectionState current) {
		boolean isHighLoad = estimateLoadStatelessOnline();
		/**
		 * neu send thread > cc -> curAvgCC = real cc
		 * send thread <= cc -> curAvgCC +1 = real CC vi 1 CC da duoc release de press tren thread hien tai
		 */
		StateSnapshot snapshot = current.createSnapshot(conf, this);
		int nextState = examineCurrentState(snapshot, isHighLoad);

		long pressTime = TimeUtils.nowNano();
		float durationFromLastChangeCC = TimeUtils.miliPassed(lastChangeCCbyLoad.get());
		switch (nextState) {
			case 1: // bat dau vao 1TPS 1CC se vao case nay -> thap tai -> +1CC -> lay room cho TPS tang
				if (debounceCC(durationFromLastChangeCC)) { // 10ms=100TPS
					if (isHighLoad) conf.changeConcurrent(Math.max(snapshot.configuredCC - ccStep, ccStep));
					else conf.changeConcurrent(increaseCCbyStep(conf, snapshot, 1));
					lastChangeCCbyLoad.set(pressTime);
				}
				break;
			case 3:
				if (debounceCC(durationFromLastChangeCC)) { // 10ms=100TPS
					if (isHighLoad) { // convert current config immediately to range near 1/4 with upper threshold to let CCPressure go to case 1+CC or case 2 stable
						// magic number 4 vi trong case be nhat 3cc, sau khi shift ve nguong 75% -> max moi = 4, cur/max = dung 75% -> fit o cc be nhat la 3 -> tot, neu nguong thap hon thi se tao ra newCur < 1/magicNumber
						// 	khong tinh voi max = 2 cur = 1 (50% case 2), max =3 cur = 1 (33% case 2)
						conf.changeConcurrent(Integer.max((int) Math.ceil((current.recentCC()) / (CC_UPPER - (CC_UPPER - CC_LOWER) / 4)), ccStep));
					} else {
						/**
						 * TH4: vi case nay dua tren highload, ma highload dua tren cpu gui ve tu server, lay tu top 1s 1 lan, nen chi trigger ham nay toi da 10/s de giam 10CC/s, tang CC thi rat nhanh, nhung giam cham
						 */
						increaseCCUtilize(conf, snapshot);
					}
					lastChangeCCbyLoad.set(pressTime);
				}
				break;
			case 2:
			default:
				break; // stable case, do nothing
		}
		lastAction.set(nextState);
		if (snapshot.configuredCC != conf.concurrent.get()
			|| lastLog.get() + 5_000_000_000L < pressTime) {
			lastChannelId.set(channelId);
			lastLog.set(pressTime);
			logger.info(report(snapshot, nextState));
		}
	}

	@Override
	public String report(BackConfigure current, PeerConnectionState state) {
		return String.format("[%s]%s", bccConfigure.report(), report(state.createSnapshot(current, this), lastAction.get()));
	}

	private String report(StateSnapshot snapshot, int curState) {
		return String.format("[CC.%s:%d|%s|sLoad:%.2f%%|avgCC:%d/%.1f%%|TPS:%d|CC:%d|u:%.2f]",
			channelId.substring(0, 3), curState, snapshot.enableControl ? "on" : "off", snapshot.peerLoadB10k / 100f, snapshot.recentCC, snapshot.ccUtilization * 100, snapshot.curTPS, snapshot.configuredCC, utilization(snapshot));
	}
}
