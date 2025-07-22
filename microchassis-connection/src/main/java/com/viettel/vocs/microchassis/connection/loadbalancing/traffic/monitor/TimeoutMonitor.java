package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor;

import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
interface SnapshotableTimeoutMonitor{
	List<Pair<String, Long>> getATimeoutBlock();
}
public interface TimeoutMonitor<KeyType> extends SnapshotableTimeoutMonitor{
	long getSendTimeoutNs();
	/**
	 * try count with msgid, that check sent map, if this msgId in sent map, then count 1 response, else it is handled by
	 * - RequestTimeoutHandler
	 * - Other handler
	 * - Duplicate Msg
	 * - removed managed id
	 * or
	 * - server push
	 * => ignore count if not found in sent map
	 *
	 * @param tobeRemoveKey / msgId
	 * @param isSuccess to monitor successRate
	 * @return null if processed -> drop, 0L if not monitor timeout, long for limited timeout
	 */
	Long ack(KeyType tobeRemoveKey, boolean isSuccess);

	/**
	 * call to {@link #watch(KeyType, long)}
	 *
	 * @param reqId
	 * @param sendTime
	 */
	void watch(KeyType reqId, AtomicLong sendTime, long timeoutMs);

	void watch(KeyType reqId, long sendTime, long timeoutMs);

	long getNextTimeoutDeadline();

	// return list of timedout entry

	Set<KeyType> getUnorderedTimeoutKeySet();


}
