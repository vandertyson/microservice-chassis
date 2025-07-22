package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor;

import com.viettel.vocs.common.os.TimeUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OrderedLinkedListTimeoutEngine extends BaseTimeoutEngine {
	ConcurrentLinkedQueue<String> timeoutOrder = new ConcurrentLinkedQueue<>();
	public OrderedLinkedListTimeoutEngine(PeerCounter counter, long sendTimeoutNs) {
		super(Objects.requireNonNull(counter), sendTimeoutNs);
	}

	/**
	 * for all request in same counter, there only 1 timeout duration so the order of request is the order of timeout
	 * => 2 structure, 1 for ordered timeout and 1 for no timeout
	 * with ordered timeout, use queue to dequeue like a slide counter
	 * with no timeout watch, use map for O(1) access speed.
	 *
	 * @param reqId
	 * @param sendTime
	 */
	@Override
	public void watch(String reqId, long sendTime, long timeoutMs) {
		getCCMap().computeIfAbsent(reqId, k -> {
			counter.recordSend(); // for the meaning, this is count first
			if(sendTime > 0L) timeoutOrder.add(reqId);
			return Pair.of(sendTime, timeoutMs);
		}); // channel ma gui duoc thi channel con song => mapSent con truy cap duoc
	}


	@Override
	public long getNextTimeoutDeadline() {
		String head = timeoutOrder.peek();
		Pair<Long, Long> sendPair = head != null ? getCCMap().get(head): null;
		Long sentTime = sendPair != null ? sendPair.getKey() : null;
		return ((sentTime != null && sentTime > 0L) ? sentTime : TimeUtils.nowNano()) + sendTimeoutNs;
	}

	/**
	 * with OrderedLinkedListTimeoutEngine, find from head timeout block
	 * @return
	 */
	@Override
	public List<Pair<String, Long>> getATimeoutBlock() {
		String headMsgId;
		ConcurrentHashMap<String, Pair<Long, Long>> ccMap = getCCMap();
		Pair<Long, Long> sentHistory;
		List<Pair<String, Long>> timedOutList = new ArrayList<>();
		while ((headMsgId = timeoutOrder.peek()) != null){
			sentHistory = ccMap.get(headMsgId);
			if (sentHistory != null && sentHistory.getKey() > 0L) {
				// trong map, check timeout
				boolean timeoutByDuration = TimeUtils.miliPassed(sentHistory.getKey()) > sentHistory.getValue() /*timeoutMs*/;
				if (TimeUtils.isTimeoutNano(sentHistory.getKey(), sendTimeoutNs) || timeoutByDuration) {
					timeoutOrder.poll();
					timedOutList.add(Pair.of(headMsgId, sentHistory.getRight()));
				} else break; // no more timeout chain, this msgId still be head of the chain
			} else timeoutOrder.poll(); // continue; // khong con trong map, handled poll tiep head moi
		}
		return timedOutList; // empty Q
	}
	@Override
	protected void removeTimeoutOrder(String reqId) {
		try {
			timeoutOrder.removeIf(item -> Objects.equals(item, reqId));
		} catch (Exception ignored){}
	}
}