/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection.config;

import com.viettel.vocs.common.os.RateClock;
import com.viettel.vocs.microchassis.base.ChassisConfig;

/**
 * @author vttek
 */
public class ChannelConfiguration {


	public ChannelConfiguration() {
	}

	public int maxRetryAcquire = ChassisConfig.RouteConfig.ConnectionRateLimiterConfig.MAXCONNECTIONRETRY_ACCQUIRE.getInt();
	public int getConnectionWaitNano = 100; // 1 thread max 100kTPS -> delta = 10micro, accuracy 99% -> wait = 1% of 10 micro
	public int channelSoRcvBuf = ChassisConfig.PeerHandlerConfig.CHANNEL_SORCVBUF.getInt();//16*1024*1024
	public int channelSoSndBuf = ChassisConfig.PeerHandlerConfig.CHANNEL_SOSNDBUF.getInt();//16*1024*1024

	public boolean epoll = ChassisConfig.PeerHandlerConfig.EPOLL_ENABLE.get();
	public boolean preferDirect = ChassisConfig.PeerHandlerConfig.PREFER_DIRECT.get();
	public boolean pooledByteBuffer = ChassisConfig.PeerHandlerConfig.POOLED_BYTEBUFFER.get();
	public int numWorkerThread = ChassisConfig.PeerHandlerConfig.NUM_WORKERTHREAD.getInt();
	public int numBossThread = ChassisConfig.PeerHandlerConfig.NUM_BOSSTHREAD.getInt();
	public int backLog = ChassisConfig.ConnectionConfig.NETTY_SO_BACK_LOG.getInt();

	public int timeoutConnectMs = ChassisConfig.ConnectionConfig.TimeOUTCONNECTMS.getInt();
	public long maxChannelWritableMs = ChassisConfig.ConnectionConfig.MaxCHANNELWRITABLEMS.get();
	public long parkWritableNano = Math.max(ChassisConfig.ConnectionConfig.PARKWRITABLE_NANO.get(), RateClock.MINOR_WAIT_NS); // 81,92 nanosec to send 1MB over 100Gbps
//	public int waitFlushNs = Math.max(ChassisConfig.RouteConfig.WAITFLUSH_NS.getInt(), 10_000); // based on 100kTPS over all conn
//	public int writeTimeoutMs = Math.max(ChassisConfig.RouteConfig.WRITETIMEOUT_MS.getInt(), 1);
	public int maxDecode = ChassisConfig.ConnectionConfig.MAX_DECODE.getInt();
	public boolean fastFlush = ChassisConfig.ConnectionConfig.FAST_FLUSH.get();
	public boolean affinity = ChassisConfig.PeerHandlerConfig.IS_AFFINITY.get();
	public boolean handlerPool = ChassisConfig.PeerHandlerConfig.isHandlerpool.get();

	public ChannelConfiguration setChannelSoRcvBuf(int channelSoRcvBuf) {
		this.channelSoRcvBuf = channelSoRcvBuf;
		return this;
	}

	public ChannelConfiguration setChannelSoSndBuf(int channelSoSndBuf) {
		this.channelSoSndBuf = channelSoSndBuf;
		return this;
	}



	public ChannelConfiguration setEpoll(boolean epoll) {
		this.epoll = epoll;
		return this;
	}

	public ChannelConfiguration setPreferDirect(boolean preferDirect) {
		this.preferDirect = preferDirect;
		return this;
	}

	public ChannelConfiguration setPooledByteBuffer(boolean pooledByteBuffer) {
		this.pooledByteBuffer = pooledByteBuffer;
		return this;
	}

	public ChannelConfiguration setNumWorkerThread(int numWorkerThread) {
		this.numWorkerThread = numWorkerThread;
		return this;
	}

	public ChannelConfiguration setNumBossThread(int numBossThread) {
		this.numBossThread = numBossThread;
		return this;
	}

	public ChannelConfiguration setBackLog(int backLog) {
		this.backLog = backLog;
		return this;
	}

	public ChannelConfiguration setTimeoutConnectMs(int timeoutConnectMs) {
		this.timeoutConnectMs = timeoutConnectMs;
		return this;
	}

	public ChannelConfiguration setMaxChannelWritableMs(long maxChannelWritableMs) {
		this.maxChannelWritableMs = maxChannelWritableMs;
		return this;
	}

	public ChannelConfiguration setParkWritableNano(long parkWritableNano) {
		this.parkWritableNano = parkWritableNano;
		return this;
	}

	public ChannelConfiguration setMaxDecode(int maxDecode) {
		this.maxDecode = maxDecode;
		return this;
	}

	public ChannelConfiguration setFastFlush(boolean fastFlush) {
		this.fastFlush = fastFlush;
		return this;
	}

	public ChannelConfiguration setAffinity(boolean affinity) {
		this.affinity = affinity;
		return this;
	}
}
