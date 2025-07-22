package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor;

import com.viettel.vocs.common.os.TimeUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * @author tiennn18
 */
public class OrderedBlocksTimeoutEngine extends BaseTimeoutEngine {
	ConcurrentLinkedQueue</* segment */ Pair<Long /* deadline */, ConcurrentLinkedQueue<String> /* chain */>> timeoutOrder = new ConcurrentLinkedQueue<>();
	private final long cycleDurationNs;
	public OrderedBlocksTimeoutEngine(PeerCounter counter, long sendTimeoutNs) {
		super(Objects.requireNonNull(counter), sendTimeoutNs);
		this.cycleDurationNs = 2_000_000L; // URLLC 1ms each hop -> RTT < 2ms -> segment must check timeout every 2ms
	}

	private MutablePair<Long /* createdTime */, ConcurrentLinkedQueue<String> /* chain */> activeSegment = newSegment(); // null protected

	private synchronized void addToActiveSegment(String reqId) {
		/** if active segment online -> add
		 *  else it is out date
		 *  	if segment has chain -> add to timeoutOrder -> create new segment -> add
		 *  	else reuse segment with new deadline, then add
 		 */
		if (TimeUtils.nowNano() > activeSegment.getLeft()  + cycleDurationNs/* timed out */){ // roll segment to queue
			if (activeSegment.getRight().isEmpty()) activeSegment.setLeft(TimeUtils.nowNano()); // reuse by reborn
			else {
				timeoutOrder.add(activeSegment); // add old
				activeSegment = newSegment();
			}
		}
		activeSegment.getRight().add(reqId);
	}
	private MutablePair<Long /* deadline */, ConcurrentLinkedQueue<String> /* chain */> newSegment() {
		return MutablePair.of(TimeUtils.nowNano() + cycleDurationNs, new ConcurrentLinkedQueue<>());
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
			if (sendTime > 0L) addToActiveSegment(reqId);
			return Pair.of(sendTime, timeoutMs);
		}); // channel ma gui duoc thi channel con song => mapSent con truy cap duoc
	}
	
	@Override
	public long getNextTimeoutDeadline() {
		Pair<Long, ConcurrentLinkedQueue<String>> headSegment = timeoutOrder.peek();
		return (headSegment != null ? headSegment.getLeft() : TimeUtils.nowNano()) + sendTimeoutNs;
	}

	/**
	 * with OrderedLinkedListTimeoutEngine, find from head timeout block
	 *
	 * @return first time out block
	 */
	@Override
	public List<Pair<String, Long>> getATimeoutBlock() { // get first block if satisfied

		if(getNextTimeoutDeadline() < TimeUtils.nowNano()) { // this head block is out date
			Pair<Long, ConcurrentLinkedQueue<String>> headSegment =	timeoutOrder.poll();
			if (headSegment != null) {

				long segmentCreatedTime = headSegment.getLeft();
				return headSegment
					.getRight().stream()
					.map(msgId -> Pair.of(msgId, segmentCreatedTime))
					.collect(Collectors.toList());
			}
		}
		return new ArrayList<>();
	}

	@Override
	protected void removeTimeoutOrder(String reqId) {
		try {
			timeoutOrder.forEach(pair -> {
				try {
					pair.getRight()/*chain*/.removeIf(item -> item.equals(reqId));
				} catch (Exception ignored){}
			});
		} catch (Exception ignored){}
	}
}
