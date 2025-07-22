package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor;

import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnState;
import com.viettel.vocs.common.os.TimeUtils;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author tiennn18
 * requirement for a good timeout engine:
 * 	1. Not depends on concurrency
 * 		ie: if high throughput incoming need monitor, if won't affect scan lag of timeout monitor thread
 * 	2. available for highthroughput: should not create manage component for each req, k-req, or depends on throughput
 * 		-> need some kind of static manage division
 * 	3. add as less as posible overhead to access check (scan), ack (insert), watch (delete)
 * 	4. add as less timeout lag to message, at max 10% accuracy (based on KPI, not timeoutDuration),
 * 		ie: KPI of req is 10ms response, timeoutDuration 5s
 * 			-> max in realtime that message must be timeout after 11ms (10ms+10%) (combine with fine tunned TimeUtils.waitUntil)
 * 	5. can handle random timeoutDuration sequense
 */
public class BaseTimeoutEngine implements TimeoutMonitor<String> {
	private final ConnState state;
	protected final PeerCounter counter;
	@Getter protected final long sendTimeoutNs;

	protected ConcurrentHashMap<String, Pair<Long,Long>> getCCMap() {
		return state.getMapSent();
	}

	public Set<String> getUnorderedTimeoutKeySet() {
		return getCCMap()
			.entrySet().stream()
			.filter(node -> node.getValue().getKey() > 0L)
			.map(Map.Entry::getKey)
			.collect(Collectors.toSet());
	}

	BaseTimeoutEngine(PeerCounter counter, long sendTimeoutNs) {
		this.counter = counter;
		this.state = this.counter.getState();
		this.sendTimeoutNs = sendTimeoutNs;
	}

	@Override
	public final void watch(String reqId, AtomicLong sendTime, long timeoutMs) {
		watch(reqId, sendTime.get(), timeoutMs);
	}
	@Override
	public void watch(String reqId, long sendTime, long timeoutMs) {
		getCCMap().computeIfAbsent(reqId, k -> {
			counter.recordSend(); // for the meaning, this is count first
			return Pair.of(sendTime, timeoutMs);
		}); // channel ma gui duoc thi channel con song => mapSent con truy cap duoc
	}

	@Override
	public long getNextTimeoutDeadline() { //  naive scan all, this work for both ordered cases and random case mix with no timeout req
		return getCCMap()
			.values().stream().map(Pair::getKey)
			.min(Long::compare)
			.orElse(TimeUtils.nowNano()) + sendTimeoutNs;
	}

	@Override
	public List<Pair<String, Long>> getATimeoutBlock() { //  naive scan all, this work for both ordered cases and random case mix with no timeout req
		return getCCMap()
			.entrySet().stream()
			.filter(e->e.getValue().getKey() > 0L && TimeUtils.isTimeoutNano(e.getValue().getKey(), sendTimeoutNs))
			.map(e-> Pair.of(e.getKey(), e.getValue().getKey())).collect(Collectors.toList());
	}


	@Override
	public Long ack(String reqId, boolean isSuccess) {
		if ("".equals(reqId) || "to3rdParty".equals(reqId)) return 0L;// of to3rdParty, or no msg managed response, still serve
		Pair<Long, Long> doneReq;
		removeTimeoutOrder(reqId);
		if ((doneReq = getCCMap().remove(reqId)) != null) {
			if(isSuccess) {
				state.stackSuccess();
				counter.recordReceive();
			}	else {
				counter.recordRelease();
				state.stackError();
			}
			return doneReq.getKey();
		}
		return null; // return null when duplicated ack => timed out and late response
	}

	protected void removeTimeoutOrder(String reqId){/*do nothing*/};
}
