package com.viettel.vocs.microchassis.util;

import com.viettel.vocs.common.log.MetricsBenchmark;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.tcp.codec.MsgHeader;
import io.netty.handler.codec.http.HttpHeaders;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author tiennn18
 */
public class MonitorManager {
	private static final Logger logger = LogManager.getLogger(MonitorManager.class);
	@Setter private Logger statisticlogger = logger;
	@Getter private static MonitorManager instance = new MonitorManager();
	// system
	MetricsBenchmark chassisClientFlush = new MetricsBenchmark();
	MetricsBenchmark waitFlushClientMb = new MetricsBenchmark();
	MetricsBenchmark entrustMb = new MetricsBenchmark();
	MetricsBenchmark serverFlushMb = new MetricsBenchmark();
	MetricsBenchmark fullDecodeQueueDropMb = new MetricsBenchmark();
	MetricsBenchmark userSHandlerMb = new MetricsBenchmark();
	MetricsBenchmark userCHandlerMb = new MetricsBenchmark();
	MetricsBenchmark clientFlushMb = new MetricsBenchmark();
	MetricsBenchmark strategyMb = new MetricsBenchmark();
	MetricsBenchmark strategySelectMb = new MetricsBenchmark();
	MetricsBenchmark lccCacheHitMb = new MetricsBenchmark();
	MetricsBenchmark lccCacheMissMb = new MetricsBenchmark();
	MetricsBenchmark algorithmMb = new MetricsBenchmark();
	MetricsBenchmark acqMb = new MetricsBenchmark();
	MetricsBenchmark serverDropMb = new MetricsBenchmark();
	MetricsBenchmark overloadMb = new MetricsBenchmark();
	MetricsBenchmark forwardTimeMB = new MetricsBenchmark();
	MetricsBenchmark wardforTimeMB = new MetricsBenchmark();
	MetricsBenchmark inServerQueueMb = new MetricsBenchmark();
	MetricsBenchmark inClientQueueMb = new MetricsBenchmark();

	public void clientFlush(long start, String strategyName) { // time from call connection.send
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) chassisClientFlush.statisticMetris(start, 0, "FlC:"+strategyName, statisticlogger);
	}
	public void waitFlushClient(long start) { // time from call connection.send
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) waitFlushClientMb.statisticMetris(start, 0, "waitFl", statisticlogger);
	}
	public void entrustClient(long start, String strategyName) { // time from call connection.send
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) entrustMb.statisticMetris(start, 0, "Entrust:"+strategyName, statisticlogger);
	}
	public void serverFlush(long start) { // time from call connection.send
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) serverFlushMb.statisticMetris(start, 0 , "Fl:S", statisticlogger);
	}
	public void fullDecodeQueueDrop(long decodeTime) { // time since receive till decision to drop
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) fullDecodeQueueDropMb.statisticMetris(decodeTime, 0, "FullDQ", statisticlogger);
	}
	public void inServerQueue(ChassisMgrableMsg msg, long queueSize, long startTime) {
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) {
			long decodeTime = msg.getDecodeTime();
			Iterable headers = msg.headers();

			if (headers instanceof HttpHeaders)
				((HttpHeaders) headers).set(ChassisConst.CustomHeader.inSqHeader, startTime - decodeTime);
			if (headers instanceof MsgHeader)
				((MsgHeader) headers).set(ChassisConst.CustomHeader.inSqHeader, startTime - decodeTime);
			inServerQueueMb.statisticMetris(decodeTime, queueSize, ChassisConst.CustomHeader.inSqHeader, statisticlogger);
		}
	}
	public void inClientReplyQueue(long decodeTime, long queueSize) {
		if(ChassisConfig.PerformanceConfig.performanceChassis.get())
			inClientQueueMb.statisticMetris(decodeTime, queueSize, ChassisConst.CustomHeader.inRqHeader, statisticlogger);
	}
	public void userSHandler(long decodeTime, long msgSize) {
		if(ChassisConfig.PerformanceConfig.performanceChassis.get())
			userSHandlerMb.statisticMetris(decodeTime, msgSize, "USH", statisticlogger);
	}
	public void userCHandler(long decodeTime, long msgSize) {
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) userCHandlerMb.statisticMetris(decodeTime, msgSize, "UCH", statisticlogger);
	}
	public void clientSend(long start) { // time from call connection.send
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) clientFlushMb.statisticMetris(start, 0, "Fl:C", statisticlogger);
	}
	public void strategySelect(long start) {
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) strategySelectMb.statisticMetris(start, 0, "sSe", statisticlogger);
	}
	public void lccCacheHit(long start) {
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) lccCacheHitMb.statisticMetris(start, 0, "lccCHit", statisticlogger);
	}
	public void lccCacheMiss(long start) {
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) lccCacheMissMb.statisticMetris(start, 0, "lccCMiss", statisticlogger);
	}
	public void strategy(long start, String strategyName) {
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) strategyMb.statisticMetris(start, 0, "Str:"+strategyName, statisticlogger);
	}
	public void algorithm(long selectStart, long avgCCofSelected, String routeStrategy) {
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) algorithmMb.statisticMetris(selectStart, avgCCofSelected, "Algo:"+routeStrategy, statisticlogger);
	}
	public void acquire(long acquireStart, int lenDestMap, String routeStrategy) {
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) acqMb.statisticMetris(acquireStart, lenDestMap, "Accq:"+routeStrategy, statisticlogger);
	}
	public void serverNotifyDrop() {
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) serverDropMb.statisticMetris(TimeUtils.nowNano(), 1, "SnDrop", statisticlogger);
	}
	public void overload(int msgInProcess) {
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) overloadMb.statisticMetris(TimeUtils.nowNano(), msgInProcess,"OverL", statisticlogger);
	}
	public void wardforTime(long decodeTime) {
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) wardforTimeMB.statisticMetris(decodeTime, 1,"Wf", statisticlogger);
	}
	public void forwardTime(long decodeTime) {
		if(ChassisConfig.PerformanceConfig.performanceChassis.get()) forwardTimeMB.statisticMetris(decodeTime, 1,"Fw", statisticlogger);
	}

	// fake test
	MetricsBenchmark dropMb = new MetricsBenchmark();
	MetricsBenchmark sendMb = new MetricsBenchmark();
	MetricsBenchmark timeoutMb = new MetricsBenchmark();
	MetricsBenchmark enQMB = new MetricsBenchmark();
	MetricsBenchmark bornMb = new MetricsBenchmark();
	MetricsBenchmark dropRetryMb = new MetricsBenchmark();
	MetricsBenchmark genfailMb = new MetricsBenchmark();
	public void clientSendDrop(){
		dropMb.statisticMetris(TimeUtils.nowNano(), 1, "CsDrop", statisticlogger);
	}
	public void sendRoundtrip(long beforeSend, String connectionMode, long effectiveTime) {
		sendMb.statisticMetris(beforeSend, effectiveTime, "SendCy:" + connectionMode,statisticlogger);
	}
	public void timeout() {
		timeoutMb.statisticMetris(TimeUtils.nowNano(), 1, "Timeout", statisticlogger);
	}
	public void fakerEnqueued(long offerTime, long queueSize) {
		enQMB.statisticMetris(offerTime, queueSize, "FakEnQ", statisticlogger);
	}
	public void generated(long genSince, long accumulateRedundantTime, long msgSize) {
		bornMb.statisticMetris(genSince, accumulateRedundantTime, "Gen:"+msgSize, statisticlogger);
	}
	public void clientRetry() {
		dropRetryMb.statisticMetris(TimeUtils.nowNano(), 1, "CRetry", statisticlogger);
	}
	public void genFulled(long tryTime) {
		genfailMb.statisticMetris(tryTime, 1, "GenFail", statisticlogger);
	}
}
