/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection;

import com.viettel.vocs.common.datatype.FuseBoolean;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.os.thread.JobWithTimeout;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisThreadManager;
import com.viettel.vocs.microchassis.codec.handler.ClientHandler;
import com.viettel.vocs.microchassis.connection.client.ChannelAttribute;
import com.viettel.vocs.microchassis.connection.client.ClientConnection;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import io.netty.channel.Channel;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;


interface TimeoutListener {

	void onTimeOut(Channel channel);
}
/**
 * do not reimplement counter here, use passed PeerCounter
 */
class TimeoutService {

	static AtomicLong rejectedCounter = new AtomicLong();
	static {
		ChassisThreadManager.timeoutExecutor.setRejectedExecutionHandler((r, executor) -> {
			long l = rejectedCounter.incrementAndGet();
			RequestTimeoutMonitor.logger.error("Timeout handler rejected. Run CompletableFuture.runAsync. countRejected={}", l);
			CompletableFuture.runAsync(r);
		});
	}
	/**
	 * service is a chain of timeout request watch with sent timestamp in order
	 * ie: 0,1,3,0,0,4,9,11,12,12,14,0,0,0,0,0,19,19,0,22,....
	 * any req with timestamp > 0 is enabled for timeout
	 * algo:
	 * link and track head of the >0 chain for quick timeout jump
	 */

	private final ClientHandler handler;
	private TimeoutListener listener;
	@Getter private final ClientConnection conn;
	private final String id;
	public String getChannelString() {
		return conn.getChannel().toString();
	}

	public String getId() {
		return id;
	}

	public Channel getChannel() {
		return conn.getChannel();
	}

	public void setListener(TimeoutListener listener) {
		this.listener = listener;
	}

	private final PeerCounter counter;

	public TimeoutService(ClientHandler userHandler, ClientConnection conn, String id) {
		this.handler = userHandler;
		this.conn = conn;
		counter = ChannelAttribute.getCounter(getChannel());
		this.id = id;
	}

	public long scanTimeoutAndGetNextScanDeadline() {
		List<Pair<String, Long>> timedOutReqs;
		while (!(timedOutReqs = counter.getATimeoutBlock()).isEmpty()) {
			if(RequestTimeoutMonitor.logger.isDebugEnabled()) RequestTimeoutMonitor.logger.debug("[TimeoutMonitor] Process timeout for {} reqs on channel={} {}", timedOutReqs.size(), getChannelString(), timedOutReqs.stream().map(Pair::getKey).collect(Collectors.toList()));
			timedOutReqs.parallelStream().map(timedOutReq -> {
				/* if chain continue, it return updated head and return next node, just loop here */
				String msgId = timedOutReq.getLeft();
//				Long sentTime = timedOutReq.getRight();
				conn.processTimeoutMsgId(conn.getChannel(), msgId); // ack in here // for remove CC
				return (Runnable) () -> {
					try {
						handler.chassisTimeoutHandle(msgId);
						counter.getState().getMapSent().remove(msgId);
					} catch (Throwable ex) {
						RequestTimeoutMonitor.logger.error(ex, ex);
					}
					try {
						if (listener != null) listener.onTimeOut(getChannel());
					} catch (Throwable ex) {
						RequestTimeoutMonitor.logger.error(ex, ex);
					}
				};
			}).forEach(runnable -> {
				if (ChassisConfig.RouteConfig.RequestTimeoutConfig.timeoutAsync.get()) {
					CompletableFuture.runAsync(runnable, ChassisThreadManager.timeoutExecutor);
				} else {
					runnable.run();
				}
			});
		}
		return counter.getNextTimeoutDeadline();
	}


}

public interface RequestTimeoutMonitor {
	Logger logger = LogManager.getLogger(RequestTimeoutMonitor.class);
	List<TimeoutService> mapService = new CopyOnWriteArrayList<>();
	FuseBoolean isRunning = new FuseBoolean(true);
	Thread threadRequestTimeout = ChassisThreadManager.RTM_Factory.newThread(new JobWithTimeout() {
		@Override
		public String getName() {
			return "RequestTimeoutMonitor";
		}
		@Override
		public void run() {
			/**
			 * moi service se co 1 threshold, nen se duyet qua head cua moi service, xu ly het cac timeout va chuyen sang service khac
			 * mapSendTime trong cung 1 service se co chung thresholdMs, nen timeout order = insert order -> luu them insert msgId history, va check timeout theo array nay
			 */

			while (isRunning.get()) {
				try {
					long targetTime = mapService.parallelStream().mapToLong(TimeoutService::scanTimeoutAndGetNextScanDeadline).min() // get earliest deadline to sleep
						.orElse(TimeUtils.nowNano() + TimeUnit.MILLISECONDS.toNanos(ChassisConfig.RouteConfig.RequestTimeoutConfig.timeoutScanIntervalMs.get()));

					TimeUtils.waitUntil(targetTime);
				} catch (Throwable e) {
					logger.error("[Timeout scanner exception]{Throwable={}}", e, e);
				}
			}
		}
	});

	static String report() {
		return String.format("RequestTimeoutMonitorThread{mapService=%d, timeoutScanIntervalMs=%s, initialMapRequestTimeoutSize=%s}", mapService.size(), ChassisConfig.RouteConfig.RequestTimeoutConfig.timeoutScanIntervalMs, ChassisConfig.RouteConfig.RequestTimeoutConfig.INITIAL_MAP_REQUEST_TIMEOUT_SIZE);
	}
	static void prepareStop() {
		logger.info("[Request timeout monitor stop]");
		mapService.clear();
	}


	static Set<String> unregister(Channel key) {
		if (key == null) {
			return null;
		}
		Optional<TimeoutService> first = mapService.stream().filter(timeoutService -> Objects.equals(timeoutService.getChannel(), key)).findFirst();
		if (first.isPresent()) {
			logger.info("Remove time out monitor. id={}, channel={}" // , timeout/sent={}",
				, first.get().getId(), key);
			mapService.remove(first.get());
			PeerCounter counter = ChannelAttribute.getCounter(first.get().getChannel());
			return counter != null ? counter.getUnorderedTimeoutKeySet() : null ;
		}
		return null;
	}

	static void registerService(String id, ClientConnection newConn, ClientHandler handler) { // registerService timeout sau khi da set counter
		Objects.requireNonNull(newConn.getChannel(), "Register null channel");
		Optional<TimeoutService> first = mapService.stream().filter(timeoutService -> Objects.equals(timeoutService.getChannel(), newConn.getChannel())).findFirst();
		if (first.isPresent()) return;
		mapService.add(new TimeoutService(handler, newConn, id));
		logger.info("Channel register request timeout monitor service. id={}, channel={}, mapServiceSize={}",
			id, newConn.getChannelString(), mapService.size());
	}

	static void registerTimeoutListerner(Channel key, TimeoutListener listener) {
		Optional<TimeoutService> first = mapService.stream().filter(timeoutService -> Objects.equals(timeoutService.getChannel(), key)).findFirst();
		if (first.isPresent()) {
			first.get().setListener(listener);
			logger.info("Channel register channel timeout monitor service. channel={}, mapTimeoutListener={}",
				key, mapService.size());
		}
	}

	static void logStatus() {
		Stream<Pair<Channel, Long>> pairStream = mapService.parallelStream().map(timeoutService -> {
			Channel channel = timeoutService.getChannel();
			PeerCounter counter = ChannelAttribute.getCounter(channel);
			long count = channel != null
				? (counter != null ? counter.getUnorderedTimeoutKeySet().size() : 0L)
				: 0L;
			return Pair.of(channel, count);
		});
		long totalCachingRequest = pairStream.mapToLong(Pair::getRight).sum();
		logger.info("Request timeout monitor status. countChannels={}, inProgress={}, rejectedHandler={}", //, stat={{}}",
			mapService.size(),
			totalCachingRequest,
			TimeoutService.rejectedCounter.get());
		if (logger.isDebugEnabled()) {
			logger.debug("[Request timeout monitor detail]{}", pairStream.map(p -> p.getLeft() + ":" + p.getRight()).collect(Collectors.joining("\n")));
		}
	}

	static void stop() {
		isRunning.fuse();
		mapService.clear();
		logger.info("[Request timeout monitor stopped]");
	}
}
