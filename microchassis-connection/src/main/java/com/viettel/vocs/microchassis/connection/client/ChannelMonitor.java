/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection.client;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisThreadManager;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.tcp.client.TcpBasedClientConnection;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChannelMonitor {

	private static final Logger logger = LogManager.getLogger(ChannelMonitor.class);
	@Getter
	private static volatile ChannelMonitor instance = new ChannelMonitor();
	private final Map<String, ChannelMonitorJob> monitorChannels = new ConcurrentHashMap<>();
	private final ExecutorService jobGrave = Executors.newCachedThreadPool();

	//	public static final long PING_SLEEP_MS = CommonUtils.getLong(ConfigurationConstant.PING_SLEEP_MS, 1000L);
	static {
		ChassisThreadManager.channelMonitorPool.setRejectedExecutionHandler((r, executor) -> {
			if (r instanceof ChannelMonitorJob) {
				logger.error("Channel check rejected. id={}", ((ChannelMonitorJob) r).conn.getMonitorID());
			} else {
				logger.error("Check rejected. {}", r);
			}
		});

	}

	private static class ChannelMonitorJob implements Runnable {
		public final Routable conn;
		long lastPingNs;
		public final AtomicBoolean running = new AtomicBoolean(false);
		@Getter
		@Setter
		private ScheduledFuture scheduledFuture;

		public ChannelMonitorJob(Routable conn) {
			this.conn = conn;
		}

		private boolean callChannelRenew() { // return !closed
			if (!conn.isClosed()) { // do not recreate if closed
				try {
					conn.connect(1);
//					conn.triggerRegister(); // da lam trong connect
//					if (channel != null && channel.isActive()) conn.onChannelRenew(channel); // da lam trong connect
				} catch (Throwable e) {
					logger.error(e);
				}
			}
			return !conn.isClosed();
		}

		private void callChannelDown() {
			try {
				conn.onChannelDown();
			} catch (Throwable e) {
				logger.error(e, e);
			}
		}


		@Override
		public void run() {
			running.set(true);
			logger.info("Channel check. id={}, channel={}, lastPing={}, connected={}",
				conn.getMonitorID(), conn.getChannelString(), lastPingNs, conn.isConnected());
			try {
				doCheck();
			} catch (Throwable ex) {
				logger.error(ex, ex);
			}
			running.set(false);
		}

		private void doCheck() {
			Channel channel = conn.getChannel();
			if (channel == null || !channel.isActive() || !channel.isOpen() || !channel.isRegistered()) {
				if (!conn.isInDnsResolve()) {
					conn.deregister(); // close if disappeared from DNS plus not connected, to give placement for other new DNS resolved to create new conn
					return;
				}
				if (channel != null) {
					logger.info("Channel is not ready. Try reconnect. id={}, isActive={}, isOpen={}, isRegistered={}"
						, conn, channel.isActive(), channel.isOpen(), channel.isRegistered());
				} else {
					logger.info("Channel is null. Try creating new channel. id={}", conn.getMonitorID());
				}
				callChannelDown();
				callChannelRenew();
				return;
			}
			if (conn instanceof TcpBasedClientConnection) {
				try {
					long interval = Long.max(conn.getConfig().pingIntervalMs, 1000);
					if (TimeUtils.isTimeoutMili(lastPingNs, interval) && conn.getConfig().pingIntervalMs > 0) { // 0 for disable, but always wait
						boolean ping = false;
						for (int i = 0; i < ChassisConfig.RouteConfig.ChannelMonitorConfig.MAX_PING_RETRY.get() && !(ping = conn.ping(channel.alloc())); i++)
							TimeUtils.waitSafeMili(interval);
						if (!ping) {
							// let it close if cannot ping after 5 time of 1000ms
//							if (channel.isActive() && channel.isOpen() && conn.getLimiter().warmingUpTPS() > 0) {
//								logger.info("Channel can not ping pong but still active. keep connection. id={}, isActive={}, isOpen={}, isRegistered={}"
//									, conn, channel.isActive(), channel.isOpen(), channel.isRegistered());
//							} else {
							logger.info("Channel can not ping pong. Try reconnect. id={}, isActive={}, isOpen={}, isRegistered={}",
								conn, channel.isActive(), channel.isOpen(), channel.isRegistered());
							callChannelDown();
							callChannelRenew();
							return;
//							}
						}
						lastPingNs = TimeUtils.nowNano();
					}
				} catch (Exception ex) {
					logger.error(ex, ex);
				}
			}
		}
	}

	public void registerMonitorObject(Routable conn) { // conn is unique
		ChannelMonitorJob channelMonitorJob = monitorChannels.computeIfAbsent(conn.getMonitorID(), monId -> new ChannelMonitorJob(conn));
		channelMonitorJob.setScheduledFuture(
			ChassisThreadManager.scheduleAtFixedRate(
				ChassisThreadManager.channelMonitorPool, channelMonitorJob,
				ChassisConfig.RouteConfig.ChannelMonitorConfig.CM_CONFIG));
		logger.info("Add channel monitor. hostname={}, port={}, id={}, hash={}",
			conn.getHostName(), conn.getPort(), conn, conn.getMonitorID());
	}

	public void unRegisterMonitorObject(Routable conn, Runnable cb) {
		ChannelMonitorJob channelMonitorJob = monitorChannels.remove(conn.getMonitorID());
		if (channelMonitorJob != null) {
			ScheduledFuture scheduledFuture = channelMonitorJob.getScheduledFuture();
			if (scheduledFuture != null) {
				scheduledFuture.cancel(true);
				jobGrave.submit(() -> {
					long startWaitCancel = TimeUtils.nowNano();
					while (channelMonitorJob.running.get()
						&& !TimeUtils.isTimeoutMili(startWaitCancel, ChassisConfig.RouteConfig.ChannelMonitorConfig.CM_CONFIG.getInterval(TimeUnit.MILLISECONDS))
					) TimeUtils.waitSafeMili(1); // wait for final task done
					if (logger.isDebugEnabled())
						logger.info("Finished ChannelMonitorJob for {}", conn.getMonitorID());
					if (cb != null) cb.run();
				});
			}
			logger.info("Remove channel monitor. hostname={}, port={}, id={} hash={}",
				conn.getHostName(), conn.getPort(), conn, conn.getMonitorID());
		}
	}

	public void stop() {
		monitorChannels.clear();
	}

	@Override
	public String toString() {
		return String.format("Channel monitor status. size=%d, queueSize=%d, active=%d, poolSize=%d",
			monitorChannels.size(), ChassisThreadManager.channelMonitorPool.getQueue().size(),
			ChassisThreadManager.channelMonitorPool.getActiveCount(), ChassisThreadManager.channelMonitorPool.getPoolSize());
	}

	public int clearQueue() {
		int size = ChassisThreadManager.channelMonitorPool.getQueue().size();
		ChassisThreadManager.channelMonitorPool.getQueue().clear();
		logger.error("Cleared {} check job", size);
		return size;
	}

	public void reschedule() {
		monitorChannels.values().forEach(monitorChannel -> {
			try {
				unRegisterMonitorObject(monitorChannel.conn, () -> {
					monitorChannel.setScheduledFuture(
						ChassisThreadManager.scheduleAtFixedRate(
							ChassisThreadManager.channelMonitorPool, monitorChannel,
							ChassisConfig.RouteConfig.ChannelMonitorConfig.CM_CONFIG));
					logger.info("channel reschedule. id={}", monitorChannel.conn.getMonitorID());
				});
			} catch (Exception ex) {
				logger.error(ex, ex);
			}
		});
	}
}
