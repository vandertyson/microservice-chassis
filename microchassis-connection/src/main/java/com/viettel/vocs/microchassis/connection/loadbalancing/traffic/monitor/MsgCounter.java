package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor;

import com.viettel.vocs.common.datatype.DataUnit;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.metrics.Metric;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.http.FullHttpMessage;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author tiennn18
 */

public class MsgCounter {
	public static final Map<String, MsgCounter> counterMap = new ConcurrentHashMap<>();
	public static MsgCounter ofClient(String clientId, String connId) {
		return counterMap.computeIfAbsent(makeClientName(clientId, connId), MsgCounter::new);
	}

	@NotNull
	public static String makeClientName(String clientId, String connId) {
		return connId != null ? "c_" + clientId + "__" + connId : ("c_" + clientId);
	}

	public static MsgCounter ofServer(String id) {
		return counterMap.computeIfAbsent("s_" + id, MsgCounter::new);
	}
	private final AtomicLong msgCounter = new AtomicLong();
	private final AtomicLong byteCounter = new AtomicLong();
	private final String peerId;
	private final Pair<String, String> label;

	private MsgCounter(String name) {
		peerId = name;
		label = Pair.of("peer_id", peerId);
	}

	public MsgCounter reset(){
		msgCounter.set(0);
		byteCounter.set(0);
		return this;
	}

	public long getMsgCount() {
		return msgCounter.get();
	}
	public double getBytesCount(DataUnit unit) {
		return DataUnit.BYTE.to(byteCounter.get(), unit);
	}
	private void stack(int bytes){
		long newValC = msgCounter.incrementAndGet(); // count at buffer
		long newValB = byteCounter.addAndGet(bytes);
		if(newValB < 0 || newValC < 0) reset();
	}
	public void stack(ChassisMgrableMsg inboundMsg) {
		stack(inboundMsg.getReadOnlyContentByteBuf());
		// TODO and other tcp/http measurements
	}
	public void stack(ByteBufHolder inboundMsg) {
		stack(inboundMsg.content());
	}
	public void stack(FullHttpMessage inboundMsg) {
		stack(inboundMsg.content().readableBytes());
		// TODO and other fullhttp measurements
	}
	public void stack(ByteBuf inboundMsg) {
		stack(inboundMsg.readableBytes());
	}

	public MsgCounter removeMetrics(Metric metric) {
		metric.remove("MsgCounter", "count", label);
		metric.remove("MsgCounter", "bytes", label);
		return this;
	}

	public MsgCounter addMetrics(Metric metric) {
		metric.set("MsgCounter", "count", getMsgCount(), label);
		metric.set("MsgCounter", "bytes", getBytesCount(DataUnit.BYTE), label);
		return this;
	}
}
