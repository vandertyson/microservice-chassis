package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

import com.viettel.vocs.microchassis.connection.loadbalancing.configure.BTC;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.BackConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.StateSnapshot;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerConnectionState;
import com.viettel.vocs.common.os.TimeUtils;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author tiennn18
 * FSM QoS xem drawio page State QoS
 * Can phan biet thoi diem request den nhieu va thoi diem ranh roi he thong
 * * 		Khi thay doi chi thay doi 1 trong 2 TPS hoac concurrent
 * * 		avgTPS > 95% TPS
 * * 	 		1TH1: && avgConcurrent > 95% ->
 * * 	 			tang concurrent +1 (he qua => tang latency => tang inflight msg -> TPS bieu hien giam) -> 1/4|5
 * * 	 			hoac
 * * 	 			neu dang thap tai server, hay nghen tham so client -> tang TPS -> 1
 * * 	 			neu dang tai cao server ->
 * * 	 				giam TPS -> 2
 * * 	 				giam CC -> 4
 * * 	 		2TH2: && avgConcurrent 70%-95% -> stable, do nothing
 * * 			3TH3: && avgConcurrent < 70% -> server ranh roi -> tang TPS -> 2
 * * 		avgTPS 70%-95% TPS
 * * 	 		4TH1: && avgConcurrent > 95% ->
 * * 	 			tai server cao, can giam concurrent -> giam TPS ve 1(loop) hoac 2
 * * 	 			tai server thap -> nghen CC tai client -> +1CC -> 5 | 2
 * *   		5TH2: && avgConcurrent 70%-95% -> normal load, do nothing
 * *  		6TH3: && avgConcurrent < 70% -> normal load, do nothing
 * *  		gop TH2 va TH3: && avgConcurrent < 95% -> normal load, do nothing
 * * 	  avgTPS < 70% TPS
 * * 	  	7TH1: && avgConcurrent > 95% maxConcurrent ->
 * * 	  		server thap tai -> nghen CC tai client -> +1CC
 * * 	  		server cao tai -> long run api in flight, incoming at client low -> giam TPS ve 4
 * * 	  	8TH2: && avgConcurrent 70%-95% maxConcurrent -> low load, do nothing
 * * 	   	9TH2: && avgConcurrent < 70% maxConcurrent -> low load, do nothing
 * * 	   	gop TH2 va TH3: && avgConcurrent < 95% -> low load, do nothing
 * <p>
 * state 6 9 khong co tuong tac ra ben ngoai
 * -> gop 6 vao 5 thanh 70-95TPS/<95CC
 * -> gop 9 vao 8 thanh <70TPS/<95CC
 *
 *
 * #9 va #6
 */
public class BTCPressure extends Pressure {
	final BTC btcConfigure;
	final float TPS_UPPER; // = 0.9f;
	final float TPS_LOWER; // = 0.60f; // > 0.5f de 1TPS + 1TPS chuyen case 2 sang case 3 +TPS sang case 5
	final float CC_UPPER; // = 0.9f; // 0.9f
	final float CC_LOWER; // = 0.3f; // = 3x reduce 0.1f
	protected final float LOWEST_TPS_UTILIZATION; // = 0.05f; // maintain operate metric at rate at least 5% ~ 100avgTPS for max 2000 peak TPS configured, if avgCC =0 -> skip this seal

	final float INCREASE_CC_THRESHOLD;
	final float INCREASE_TPS_THRESHOLD;
	@Override
	public float utilization(StateSnapshot snapshot) {
		/**
		 * avg(tpsU + ccU)
		 * tpsU cang be cang tot, base 1
		 * ccU cang be cang tot, base 1
		 *  # khong + server load vao day, server load bieu hien ra ben ngoai bang InSQ va CC hold
		 *
		 * at low speed, avgCC usurally = 0 while snapshot mapSent, so utilization should not multiply directly
		 * 	but if avg, bias load to high utilization, change to multiply to bias to partitial low utilization -> balance all utilization
		 * 		=> multiply with avgTPS
		 * 	u = (avgTPS + avgTPS*avgCC)/2
		 * 		avgCC = 0 -> u=avgTPS/2 else > avgTPS/2 -> uu tien cac ket noi co avgCC=0
		 */
		return (snapshot.tpsUtilization + snapshot.tpsUtilization * snapshot.ccUtilization)/2;
	}

	private String report(StateSnapshot snapshot, int curState){
		return String.format("[BTC.%s:%d|%s|sLoad:%.2f%%|avgTPS:%d/%d%%|avgCC:%d/%.1f%%|TPS:%d|CC:%d|u:%.2f]",
			channelId.substring(0, 3), curState, snapshot.enableControl ? "on" : "off", snapshot.peerLoadB10k / 100f, snapshot.curTPS, (int) (snapshot.tpsUtilization * 100), snapshot.recentCC, snapshot.ccUtilization * 100, snapshot.configuredTPS, snapshot.configuredCC, utilization(snapshot));
	}
	@Override
	public String report(BackConfigure current, PeerConnectionState state) {
		return String.format("[%s]%s", btcConfigure.report(),report(state.createSnapshot(current, this), lastAction));
	}

	public BTCPressure(String channelId, BTC btcConfigure) {
		super(channelId, btcConfigure);
		this.btcConfigure = Objects.requireNonNull(btcConfigure, "Require backPressureConfig for btc strategy");
		TPS_UPPER = btcConfigure.tpsUpperPct/100f;
		TPS_LOWER = btcConfigure.tpsLowerPct/100f;
		CC_UPPER = btcConfigure.ccUpperPct/100f;
		CC_LOWER = btcConfigure.ccLowerPct/100f;
		LOWEST_TPS_UTILIZATION = btcConfigure.lowestTpsUtilizationPct/100f;
		PEER_HIGHLOAD_THRESHOLD = btcConfigure.highload * 100;
		assert CC_LOWER > LOWEST_CC_UTILIZATION;
		/**
		 * boi vi isHighLoad dua vao top CPU cua server, ma top lay 1s 1 lan nen se cham, nen can lowestUtilization de cat giam config neu config vot qua nhanh trong dieu kien tang config ma request den qua nhanh, + qua nhieu
		 */
		assert TPS_LOWER > LOWEST_TPS_UTILIZATION;
		INCREASE_CC_THRESHOLD = (0.2f + 1) / CC_UPPER; // more 20%
		INCREASE_TPS_THRESHOLD = (0.2f + 1) / TPS_UPPER; // more 20%
	}

	@Override
	public BTC getConfig() {
		return btcConfigure;
	}

	@Override
	public BackConfigure newBackConfig() {
		return new BackConfigure(btcConfigure.initTPS, btcConfigure.initCC);
	}




	protected int examineCurrentState(StateSnapshot snapshot) {
		if (snapshot.tpsUtilization >= TPS_UPPER) { // case 1 2 3
			return (snapshot.ccUtilization >= CC_UPPER) ? 1 : ((snapshot.ccUtilization < CC_LOWER) ? 3 : 2);
		} else if (snapshot.tpsUtilization < TPS_LOWER) { // case 7 8 9
			return (snapshot.ccUtilization >= CC_UPPER) ? 7 : (
				snapshot.ccUtilization < CC_LOWER ? 9 : 8
			);
		} else { // case 4 5 6
			return (snapshot.ccUtilization >= CC_UPPER) ? 4 : (
				snapshot.ccUtilization < CC_LOWER ? 6 : 5
			);
		}
	}

	private int lastAction;
	public int getCurrentFsmState(){
		return lastAction;
	}

	private int adjustCCbyStep(int curCC, float ccUtilization, int nStep) {
		return Math.max(curCC + (
			nStep > 0 && (
				ccUtilization == 0 // 0 so dont change, need send to have clue
				|| 1/ccUtilization > INCREASE_CC_THRESHOLD
			) // check when add
				? 0
				: ccStep * nStep
		), ccStep);
	}

	private int adjustTPSbyStep(int curTps, float tpsUtilization, int nStep) {
		return Math.max(curTps + (
			nStep > 0 && (
				tpsUtilization == 0 // 0 so dont change, need send to have clue
				|| 1/tpsUtilization > INCREASE_TPS_THRESHOLD // check when add
			)
				? 0
				: tpsStep * nStep
		), tpsStep);
	}

	protected final AtomicLong lastChangeCCbyLoad = new AtomicLong(0);
	protected final AtomicLong lastChangeTPSbyLoad = new AtomicLong(0);

	private void approveCurCCSnapshot(BackConfigure conf, StateSnapshot snapshot){
		if(!snapshot.enableControl) conf.changeTPS(Math.max(snapshot.curTPS / tpsStep * tpsStep, tpsStep)); // if enable control while at 2 -> avgTPS >>> confTPS -> set confTPS = avgTPS
	}
	private void approveCurTPSSnapshot(BackConfigure conf, StateSnapshot snapshot){
		if(!snapshot.enableControl) conf.changeConcurrent(Math.max(snapshot.recentCC / ccStep * ccStep, ccStep)); // if enable control while at 4 -> avgCC >>> confCC -> set confCC = avgCC to reduce
	}
	@Override
	public void press(BackConfigure conf, PeerConnectionState current) {
		boolean isHighLoad = estimateLoadStatelessOnline();
		StateSnapshot snapshot = current.createSnapshot(conf, this);
		int nextState = examineCurrentState(snapshot);
//		boolean isChange = nextState != lastAction;
		long pressTime = TimeUtils.nowNano();
		float durationFromLastChangeCC = TimeUtils.miliPassed(lastChangeCCbyLoad.get());
		float durationFromLastChangeTPS = TimeUtils.miliPassed(lastChangeTPSbyLoad.get());

		switch (nextState) {
			case 1: // bat dau vao 1TPS 1CC se vao case nay -> thap tai -> +1CC -> lay room cho TPS tang
				if (debounceCC(durationFromLastChangeCC)) { // 10ms=100TPS
					if (isHighLoad) conf.changeConcurrent(adjustCCbyStep(snapshot.configuredCC, snapshot.ccUtilization,-1));
					else { // thap tai
						if (debounceTPS(durationFromLastChangeTPS)) {
							if(snapshot.ccUtilization > 1) approveCurCCSnapshot(conf, snapshot);
							if(snapshot.tpsUtilization > 1) approveCurTPSSnapshot(conf, snapshot);
							else conf.changeTPS(adjustTPSbyStep(snapshot.configuredTPS, snapshot.tpsUtilization,-1));
							lastChangeTPSbyLoad.set(pressTime);
						}
						conf.changeConcurrent(adjustCCbyStep(snapshot.configuredCC, snapshot.ccUtilization, 1));
					}
					lastChangeCCbyLoad.set(pressTime);
				}
				break;
			case 4:
				if (isHighLoad) {
					if (lastAction == 1) {
						if (debounceCC(durationFromLastChangeCC)) {
							conf.changeConcurrent(adjustCCbyStep(snapshot.configuredCC, snapshot.ccUtilization, 1));
							lastChangeCCbyLoad.set(pressTime);
						}
					} else if (debounceTPS(durationFromLastChangeTPS)) {
						conf.changeTPS(adjustTPSbyStep(snapshot.configuredTPS, snapshot.tpsUtilization,-1));
						lastChangeTPSbyLoad.set(pressTime);
					}
				} else if (debounceCC(durationFromLastChangeCC)) {
					if(snapshot.ccUtilization > 1) approveCurTPSSnapshot(conf, snapshot);
					else conf.changeConcurrent(adjustCCbyStep(snapshot.configuredCC, snapshot.ccUtilization, 1));
					lastChangeCCbyLoad.set(pressTime);
				}
				break;
			case 7: // co 2 action co the thuc hien +1CC -> 8 stable hoac -TPS ->4, uu tien ve stable 8
				if (isHighLoad) {
					if (debounceTPS(durationFromLastChangeTPS)) {
						conf.changeTPS(adjustTPSbyStep(snapshot.configuredTPS, snapshot.tpsUtilization,-1));
						lastChangeTPSbyLoad.set(pressTime);
					}
				} else if (debounceCC(durationFromLastChangeCC)) {
					conf.changeConcurrent(adjustCCbyStep(snapshot.configuredCC, snapshot.ccUtilization, 1));
					lastChangeCCbyLoad.set(pressTime);
				}
				// khong tang utilization TPS o day, cho ve 8 roi giam // increaseTPSUtilize(conf, current, tpsUtilization);
				break;
			case 3:
				conf.changeTPS(adjustTPSbyStep(snapshot.configuredTPS, snapshot.tpsUtilization, 1));
				increaseCCUtilize(conf, snapshot);
				break;
			case 6:
			case 9:
				/* 3 6 9 when TPS in mid and low, cc too low -> increase cc utilize */
				increaseCCUtilize(conf, snapshot);
			case 8:
				/* 8 9 when cc in mid and low, tps too low -> increase TPS utilize */
				increaseTPSUtilize(conf, snapshot);
				break;
			case 2: // stable case, do nothing
				if(snapshot.tpsUtilization > 1) approveCurCCSnapshot(conf, snapshot);
				break;
			default: // 5 6 8 9
				break; // stable case, do nothing
		}
		lastAction = nextState;

		if (!channelId.equals(lastChannelId.get())
			&& (
			snapshot.configuredCC != conf.concurrent.get() || snapshot.configuredTPS != conf.getTPS())
			|| lastLog.get() + 5_000_000_000L < pressTime
		) {
			lastLog.set(pressTime);
			lastChannelId.set(channelId);
			logger.info(report(snapshot, nextState));
		}
	}

	private void increaseTPSUtilize(BackConfigure conf, StateSnapshot snapshot) {
//		if(current.getAvgTPS() > 0) {
		if(snapshot.tpsUtilization > 0) {
			if (LOWEST_TPS_UTILIZATION > snapshot.tpsUtilization && snapshot.enableControl) // too low, cut down to lower to control
				conf.changeTPS(((int) Math.ceil(snapshot.tpsUtilization/TPS_LOWER/tpsStep)+1)*tpsStep); // min is 1 step so ok no need max check
			else if (TPS_LOWER > snapshot.tpsUtilization) // continue to drop or 1 return after lowest cut down => lowest cut down to lower + 1 step
				conf.changeTPS(adjustTPSbyStep(snapshot.configuredTPS, snapshot.tpsUtilization,-1));
		}
	}

	private void increaseCCUtilize(BackConfigure conf, StateSnapshot snapshot) {
		if (snapshot.ccUtilization > 0) {
			if(LOWEST_CC_UTILIZATION > snapshot.ccUtilization && snapshot.enableControl)
				conf.changeConcurrent(((int) Math.ceil(snapshot.ccUtilization/CC_LOWER/ccStep)+1)*ccStep); // min is 1 step so ok no need max check
			else if (CC_LOWER > snapshot.ccUtilization)
				conf.changeConcurrent(adjustCCbyStep(snapshot.configuredCC, snapshot.ccUtilization, -1));
		}
	}
}
