package com.viettel.vocs.microchassis.http.client;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.SiteAPI;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import io.netty.handler.codec.http.HttpMethod;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class HealthCheckMonitor {
	private static final Logger logger = LogManager.getLogger(HealthCheckMonitor.class);
	public static final HealthCheckMonitor instance = new HealthCheckMonitor();
	private static final AtomicLong rejected = new AtomicLong();
	private final Map<Long, HealthcheckTask> mapObj = new ConcurrentHashMap<>();
	private static final Map<Long, ScheduledFuture<?>> mapScheduled = new ConcurrentHashMap<>();
	private static class HealthcheckTask {
		@Getter final Endpoint endpoint;
		@Getter final Map<String, Monitorable> listeners = new ConcurrentHashMap<>();
		@Getter @Setter private long lastHealthCheck;
		@Getter @Setter private String checkPath;
		@Getter @Setter private int maxRetry;
		@Getter @Setter private int checkInterval;
		@Getter @Setter private long lastLog;
		@Getter @Setter private long timeoutMs;
		public HealthcheckTask(Endpoint e) {
			this.endpoint = e;
		}

		public MonitorJob makeJob() {
			return new MonitorJob(this);
		}
	}
	private static final ScheduledThreadPoolExecutor healthcheckScheduler = ChassisConfig.HealthcheckConfig.H1Healthcheck_CONFIG.makeScheduledPool((r, executor) -> {
		if (r instanceof MonitorJob) {
			MonitorJob r1 = (MonitorJob) r;
			try {
				long l = System.currentTimeMillis() - r1.check.lastHealthCheck;
				logger.info("Monitor job rejected. URI={} lastCheckMsAgo={}", r1.check.getEndpoint(), l);
				if (l >= (long) r1.check.checkInterval * 1000 * ChassisConfig.HealthcheckConfig.healthcheck_maxHangCount.get()) {
					ScheduledFuture<?> scheduledFuture = mapScheduled.remove(r1.id);
					if (scheduledFuture != null) {
						scheduledFuture.cancel(true);
					}
					mapScheduled.put(r1.id, ((ScheduledThreadPoolExecutor)executor).scheduleAtFixedRate(r1, 0, r1.check.checkInterval, TimeUnit.SECONDS));
					logger.info("Monitor job hang detect. Cancel and reschedule. URI={} lastCheckMsAgo={}", r1.check.getEndpoint(), l);
				}
			} catch (Exception ex) {
				logger.error(ex, ex);
			}
		} else {
			logger.error("Job rejected");
		}
		long l = rejected.incrementAndGet();
		if (l >= Long.MAX_VALUE - 1000) {
			rejected.set(0);
		}
	});

	public static boolean healthCheck(Endpoint uri, String path, long timeoutMs, int maxRetry) {
		boolean healthy = false;
		int count = 0;
		do {
			try {
				HttpInstanceClient client = ConnectionManager.getDefaultHttpInstance();
				count++;
				healthy = ChassisConfig.HealthcheckConfig.HEALTHY_HTTP_CODE.contains(
					client.sendSync(uri, client.createReq(HttpMethod.GET, path), timeoutMs)
						.status().code());
			} catch (Exception e) {
				logger.error(e, e);
			}
		} while (!healthy && count < maxRetry);
		return healthy;
	}

	public void register(HttpClient client, Endpoint... uris) {
		HttpClientOptions config = client.getConfig();
		if (config.healthCheck != null) register(new Monitorable() {
			@Override
			public String getId() {
				return config.id;
			}

			@Override
			public int getCheckInterval() {
				return config.healthCheck.healthCheckIntervalSec;
			}

			@Override
			public void onURIup(Endpoint uri) {
				((SiteAPI) client).getConnections()
					.stream().filter(c -> c.getEndpoint().equals(uri)).forEach(c -> c.setEnable(true));
			}

			@Override
			public void onURIdown(Endpoint uri) {
				((SiteAPI) client).getConnections().stream().filter(c -> c.getEndpoint().equals(uri)).forEach(c -> c.setEnable(false));
			}

			@Override
			public int getMaxRetry() {
				return config.healthCheck.healthCheckMaxRetry;
			}

			@Override
			public String getCheckPath() {
				return config.healthCheck.path;
			}

			@Override
			public long getTimeoutMs() {
				return config.healthCheck.healthCheckTimeoutMs;
			}
		}, uris);
	}

	public void register(Monitorable mo, Endpoint... uris) {
		if (uris == null || uris.length == 0) {
			return;
		}
		Arrays.stream(uris).forEach(endpoint -> {
			Optional<HealthcheckTask> first = mapObj.values().stream().filter(f -> f.getEndpoint().equals(endpoint)).findFirst();
			if (first.isPresent()) {
				Map<String, Monitorable> listeners = first.get().getListeners();
				if (!listeners.containsKey(mo.getId())) {
					listeners.put(mo.getId(), mo);
					logger.info("New listener added. URI={}, listenerId={}, listeners={}", endpoint, mo.getId(), listeners.size());
				} else {
					logger.info("Listener already added. URI={}, listenerId={}, listeners={}", endpoint, mo.getId(), listeners.size());
				}
			} else {
				HealthcheckTask check = new HealthcheckTask(endpoint);
				check.getListeners().put(mo.getId(), mo);
				check.setCheckPath(mo.getCheckPath());
				check.setMaxRetry(mo.getMaxRetry());
				check.setCheckInterval(mo.getCheckInterval());
				check.setTimeoutMs(mo.getTimeoutMs());

				MonitorJob job = check.makeJob();
				ScheduledFuture<?> schedule = healthcheckScheduler.scheduleAtFixedRate(job, 0, check.getCheckInterval(), TimeUnit.SECONDS);
				mapObj.put(job.getId(), check);
				mapScheduled.put(job.getId(), schedule);
				logger.info("New health check scheduled. URI={}, intervalSec={}, listeners={}, size={}",
					endpoint, mo.getCheckInterval(), check.getListeners().size(), mapObj.size());
			}
		});
	}

	private static class MonitorJob implements Runnable {
		private static final AtomicLong jobIdGenerator = new AtomicLong();
		private final HealthcheckTask check;

		@Getter private final long id = jobIdGenerator.incrementAndGet();

		public MonitorJob(HealthcheckTask check) {
			this.check = check;
		}

		@Override
		public void run() {
			long start = TimeUtils.nowNano();
			boolean doLog = TimeUtils.isMiliTimeoutSec(check.getLastLog(), ChassisConfig.HealthcheckConfig.healthcheck_logIntervalSec.get());
			if (doLog) {
				logger.info("Health check thread is running. URI {}. Last check {}. Rejected {}",
					check.getEndpoint(), check.getLastHealthCheck(), rejected.get());
				check.setLastLog(start);
			}
			check.setLastHealthCheck(start);
			if (logger.isDebugEnabled() || doLog)
				logger.debug("Health check started. URI={}", check.getEndpoint());
			boolean isHealthy = healthCheck(check.getEndpoint(), check.getCheckPath(), check.getTimeoutMs(), check.getMaxRetry());
				check.getListeners().values().forEach(value -> {
					try {
						if (isHealthy) value.onURIup(check.getEndpoint());
						else value.onURIdown(check.getEndpoint());
					} catch (Throwable ex) {
						logger.error(ex, ex);
					}
				});
			if (logger.isDebugEnabled() || doLog)
				logger.debug("Health check finished. URI={}, isHealthy={}, timeMs={}, listeners={}",
					check.getEndpoint(), isHealthy, TimeUtils.miliPassed(start), check.getListeners().size());
		}
	}

	public void unregister(HttpClient client) {

	}
	public void unregister(Monitorable mo) {
		Set<Long> toRemove = new HashSet<>();
		mapObj.forEach((key, value) -> {
			Monitorable remove = value.getListeners().remove(mo.getId());
			if (remove != null) {
				logger.info("Remove health check listener. id={}, uri={}, remain={}", remove.getId(), value.getEndpoint(), value.getListeners().size());
			}
			if (remove != null && value.getListeners().isEmpty()) {
				toRemove.add(key);
			}
		});
		if (!toRemove.isEmpty()) {
			toRemove.forEach(aLong -> {
				HealthcheckTask remove = mapObj.remove(aLong);
				ScheduledFuture job = mapScheduled.remove(aLong);
				if (job != null) {
					job.cancel(true);
					logger.info("Remove health check job. uri={}, remainJob={}", remove.getEndpoint(), mapScheduled.size());
				}
			});
		}
	}

	@Override
	public String toString() {
		return "HealthCheckMonitor{" +
			"logIntervalSec=" + ChassisConfig.HealthcheckConfig.healthcheck_logIntervalSec +
			", healthyHttpCode=" + ChassisConfig.HealthcheckConfig.HEALTHY_HTTP_CODE +
			", rejected=" + rejected.get() +
			", scheduled=" + mapScheduled.size() +
			", monitored=[" + mapObj.values().stream().map(f -> f.getEndpoint().toString()).collect(Collectors.joining(", ")) +
			"]}";
	}
}
