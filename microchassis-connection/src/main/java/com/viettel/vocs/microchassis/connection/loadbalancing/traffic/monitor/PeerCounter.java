package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor;

import com.viettel.vocs.common.log.slideMonitor.AvgSlideMonitor;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author tiennn18
 */

interface ResponseCountable {
	long recordSend();
	long getSent();
	long getReceived();
	long recordReceive();
	long getReleased();
	long recordRelease();
	PeerConnectionState timeout(String messageId);
}

public class PeerCounter
	implements ResponseCountable, TimeoutMonitor<String> {
	// Long du de dem request trong 100 nam voi 2,9ty TPS
	private final AtomicLong sent = new AtomicLong(0);
	private final AtomicLong received = new AtomicLong(0);
	private final AtomicLong release = new AtomicLong(0);
	protected final AvgSlideMonitor avgLatencyMicroS = new AvgSlideMonitor();
	private boolean pause = false;

	public final TimeoutMonitor<String> timeoutMgr;

	public void disableCount() {
		pause = true;
	}

	public void enableCount() {
		pause = false;
	}

	public long recordSend() {
		return (pause) ? sent.get() : sent.incrementAndGet();
	}

	public long recordReceive() {
		return (pause) ? received.get() : received.incrementAndGet();
	}

	public long recordRelease() {
		return (pause) ? release.get() : release.incrementAndGet();
	}

	@Override
	public long getReceived() {
		return received.get();
	}

	public int getSentTPS() {
		return state.getAvgSend();
	}
	public int getSentCC() {
		return state.curCC();
	}
	public long getRecentSentCC() {
		return state.recentCC();
	}

	@Override
	public long getReleased() {
		return release.get();
	}

	@Override
	public long getSent() {
		// center count for both mode
		return sent.get();
	}
	public float getAvgLatencyMs(){
		return avgLatencyMicroS.get()/1000f;
	}
	public PeerConnectionState getState() {
		return state;
	}

	private final PeerConnectionState state;

	@Getter
	protected final long sendTimeoutNs;

	/**
	 * Create counter on each specific (renew) channel, routeStrategy can be change, so routeStrategy determined at the time channel created,
	 * <p>
	 * //	 * @param activeConnMode hence activeConnMode create from routeStrategy at runtime, then state created at runtime too
	 * // to change state or connMode, create new PeerCounter
	 */
	protected PeerCounter(PeerConnectionState state, long sendTimeoutMs) {
		this.state = state;
		sendTimeoutNs = TimeUnit.MILLISECONDS.toNanos(sendTimeoutMs);
		switch (ChassisConfig.RouteConfig.RequestTimeoutConfig.TIMEOUT_ENGINE.get()){
			case "base":
				timeoutMgr = new BaseTimeoutEngine(this, sendTimeoutNs);
				break;
			case "block": timeoutMgr = new OrderedBlocksTimeoutEngine(this, sendTimeoutNs);
				break;
			case "ll":
			default: timeoutMgr = new OrderedLinkedListTimeoutEngine(this, sendTimeoutNs);
				break;
		}
	}
	public PeerCounter(StrategyConfigure configure, long sendTimeoutMs) {
		this(ConnectionMode.newState(configure), sendTimeoutMs);
	}
	public boolean isPause() {
		return pause;
	}

	public void reset() {
		received.set(0);
		sent.set(0);
		release.set(0);
	}



	public long getProcessing() {
		return received.get() - (sent.get() + release.get());
	}


	private final AtomicLong nextCheckTimeout = new AtomicLong(0);

	@Override
	public final Long ack(String msgId, boolean isSuccess) {
		Long sentTime = timeoutMgr.ack(msgId, isSuccess);
		if(isSuccess && sentTime != null && sentTime > 0)
			avgLatencyMicroS.stack((long)TimeUtils.microPassed(sentTime));
		return sentTime;
	}
	@Override
	public PeerConnectionState timeout(String messageId) {
		ack(messageId, false);
		return state;
	}
	@Override
	public final void watch(String reqId, AtomicLong sendTime, long timeoutMs) {
		timeoutMgr.watch(reqId, sendTime, timeoutMs);
	}

	@Override
	public final void watch(String reqId, long sendTime, long timeoutMs) {
		timeoutMgr.watch(reqId, sendTime, timeoutMs);
	}

	@Override
	public final long getNextTimeoutDeadline() {
		return timeoutMgr.getNextTimeoutDeadline();
	}

	@Override
	public final List<Pair<String, Long>> getATimeoutBlock() {
		return timeoutMgr.getATimeoutBlock();
	}

	@Override
	public final Set<String> getUnorderedTimeoutKeySet() {
		return timeoutMgr.getUnorderedTimeoutKeySet();
	}

}
