package com.viettel.vocs.mano.service;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.mano.model.MetricPushBody;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MetricPushThread extends Thread {

	public static final ThreadManager.ScheduledExecutor metricPushPool = ChassisConfig.MetricConfig.METRIC_PUSH_CONFIG.makeScheduledPool();
	private static final Logger logger = LogManager.getLogger(MetricPushThread.class);
	private static final AtomicInteger id = new AtomicInteger();
	private final Queue<Pair<String, List<MetricPushBody>>> queueReadEvent;
	private final MetricPusher appender;
	private static boolean isRunning = true;
	protected static final Map<String, AtomicInteger> countRetry = new ConcurrentHashMap<>();
	private final int timeoutSec;
	private final int maxQueueSize;
	private CompletableFuture<Boolean> push;

	public MetricPushThread(Queue<Pair<String, List<MetricPushBody>>> queue,
													MetricPusher appender,
													int timeoutSec, int maxQueueSize) {
		super(ChassisConfig.MetricConfig.METRIC_PREFIX_THREAD_NAME.get() + id.getAndIncrement());
		this.queueReadEvent = queue;
		this.appender = appender;
		this.timeoutSec = timeoutSec;
		this.maxQueueSize = maxQueueSize;
	}


	@Override
	public void run() {
		while (isRunning) {
			try {
				TimeUtils.waitSafeMili(ChassisConfig.MetricConfig.SLEEP_PER_PUSH.get());
				if (queueReadEvent.isEmpty()) {
					continue;
				}
				Pair<String, List<MetricPushBody>> peek = null;
				try {
					peek = queueReadEvent.remove();
				} catch (Throwable ex) {
					continue;
				}
				if (peek == null) {
					continue;
				}
				List<MetricPushBody> remove = peek.getValue();
				push = appender.push(peek);
				try {
					Boolean aBoolean = push.get(timeoutSec, TimeUnit.SECONDS);
					if (aBoolean) {
						countRetry.remove(peek.getKey());
					} else {
						retryPush(peek.getKey(), remove);
					}
				} catch (Throwable ex) {
					retryPush(peek.getKey(), remove);
					logger.error(ex, ex);
				}
			} catch (Throwable e) {
				logger.error(e, e);
			}
		}
	}

	private void retryPush(String next, List<MetricPushBody> remove) {
		AtomicInteger counter = countRetry.computeIfAbsent(next, s -> new AtomicInteger());
		if (counter.get() <= ChassisConfig.MetricConfig.MAX_RETRY_PUSH.get()) {
			if (queueReadEvent.size() > maxQueueSize) {
				dropQueue(next, remove);
			} else {
				queueReadEvent.offer(Pair.of(next, remove));
				counter.incrementAndGet();
			}
		} else {
			dropQueue(next, remove);
		}
	}

    private void dropQueue(String next, List<MetricPushBody> remove) {
        logger.error("Drop metric push. Max retry reached. key={}, size={}, max={}", next, remove.size(), ChassisConfig.MetricConfig.MAX_RETRY_PUSH);
        remove.clear();
        queueReadEvent.remove(next);
        countRetry.remove(next);
    }

	public void stopGracefully() {
		try {
			long l = TimeUtils.nowNano();
			boolean graceful = true;
			int size = queueReadEvent.size();
			while (!queueReadEvent.isEmpty()) {
				if (TimeUtils.isTimeoutMili(l, ChassisConfig.MetricConfig.GRACE_TIME.get())) {
					graceful = false;
					break;
				}
				TimeUtils.waitSafeMili(10);
			}
			l = TimeUtils.nowNano();
			while (push != null && !push.isDone()) {
				if (TimeUtils.isTimeoutMili(l, ChassisConfig.MetricConfig.GRACE_TIME.get())) {
					graceful = false;
					break;
				}
				TimeUtils.waitSafeMili(10);
			}
			isRunning = false;
			System.out.println(new StringBuilder()
				.append("shutdown metric push. thread=").append(getName())
				.append(", graceful=").append(graceful)
				.append(", remain=").append(size)
				.append(", timeMs=").append(TimeUtils.miliPassed(l)));
		} catch (Throwable ex) {
			logger.error(ex, ex);
		}
	}
}
