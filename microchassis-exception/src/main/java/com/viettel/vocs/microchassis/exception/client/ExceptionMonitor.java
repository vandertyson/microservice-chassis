package com.viettel.vocs.microchassis.exception.client;

import com.viettel.vocs.common.LRUMapAsCache;
import com.viettel.vocs.common.LRUMapAsCache.DropHandler;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.exception.appender.Appender;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmEventBuilder;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes;
import com.viettel.vocs.microchassis.exception.data_model.message.MessageBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public interface ExceptionMonitor {
	Logger logger = LogManager.getLogger(ExceptionMonitor.class);
	Object lock = new Object();
	BlockingQueue<MessageBuilder> queueWriteEvent = new ArrayBlockingQueue<>(ChassisConfig.ExceptionClientConfig.ClientConfig.CLIENT_EX_QUEUE_CAPACITY.getInt());
	Map<String, PushException> mapTheadSender = new HashMap<>();
	int QUEUE_ACTION_TIME_OUT = 500;

	Map<String, Long> mapCacheTime = Collections.synchronizedMap(new LRUMapAsCache(ChassisConfig.ExceptionClientConfig.ClientConfig.MAP_CACHE_ID_CAPACITY.getInt()));
	AtomicLong countDrop = new AtomicLong();
	List<DropHandler> customDropHandler = new ArrayList<>();
	Map<String, String> cacheAlarmId = Collections.synchronizedMap(new LRUMapAsCache(ChassisConfig.ExceptionClientConfig.ClientConfig.MAP_CACHE_ID_CAPACITY.getInt()).setDropHandler(arg0 -> {
		try {
			String cachingKey = arg0.getKey().toString();
			String messageId = arg0.getValue().toString();
			Long remove = mapCacheTime.remove(cachingKey);
			long time = 0;
			if (remove != null) {
				time = System.currentTimeMillis() - remove;
			}
			logger.error("Alarm caching key is dropped. cachingKey={}, messageId={}, countDrop={}, cacheDurationMs={}",
				cachingKey, messageId, countDrop.incrementAndGet(), time);
			customDropHandler.forEach(dropHandler -> {
				try {
					dropHandler.onKeyDrop(arg0);
				} catch (Throwable ex) {
					logger.error(ex, ex);
				}
			});
		} catch (Throwable ex) {
			logger.error(ex, ex);
		}
	}));

	class PushException extends Thread {
		private static final Logger logger = LogManager.getLogger(PushException.class);
		private static final AtomicInteger id = new AtomicInteger();
		private final Appender appender;
		private boolean isRunning = true;

		public PushException(Appender appender) {
			super(ChassisConfig.ExceptionClientConfig.EXCEPTION_PREFIX_THREAD_NAME.get() + id.getAndIncrement());
			this.appender = appender;
		}

		@Override
		public void run() {
			try {
				while (isRunning) {
					try {
						MessageBuilder messageBuilder = queueWriteEvent.poll(QUEUE_ACTION_TIME_OUT, TimeUnit.MILLISECONDS);

						if (messageBuilder != null) {
							doSend(messageBuilder.getBytesOfJson(), messageBuilder.getId());

							if (logger.isDebugEnabled())
								logger.debug("capture alarm");
						}
					} catch (Throwable e) {
						logger.error(e, e);
					}
				}
			} finally {
				if (logger.isDebugEnabled()) logger.debug("Close appender");
				appender.close();
			}
		}

		private void doSend(byte[] data, String reqID) {
			try {
				appender.sendMessage(reqID, data, ChassisConfig.ExceptionClientConfig.RestConfig.REST_ALARM_ENDPOINT.get());
			} catch (Throwable e) {
				logger.error(e, e);
			}
		}

		public void shutdown() {
			logger.debug(this.getName() + " shutdown");
			this.isRunning = false;
		}
	}


	static void captureAlarm(Throwable throwable, boolean isCached, String keyToCache) {
		captureAlarm(new AlarmEventBuilder().withThrowable(throwable), isCached, keyToCache);
	}

	static boolean captureAlarm(MessageBuilder messageBuilder, boolean isCached, String keyToCache) {
		synchronized (lock) {
			try {
				if (messageBuilder.getNotificationType() == AlarmTypes.NotificationType.ALARM_CLEARED_NOTIFICATION) {
					cacheAlarmId.remove(keyToCache);
					mapCacheTime.remove(keyToCache);
				}

				String messageID = messageBuilder.getId();
				if (isCached) {
					if (!cacheAlarmId.containsKey(keyToCache)) {
						return doCache(messageBuilder, keyToCache, messageID);
					} else {
						//Check cache time
						Long cacheTime = mapCacheTime.get(keyToCache);
						if (cacheTime != null && TimeUtils.isMiliTimeoutMili(cacheTime, ChassisConfig.ManoConfig.AlarmConfig.ALARM_CACHE_MAX_MS.get())) {
							String put = cacheAlarmId.remove(keyToCache);
							logger.info("Alarm is cached more than {} ms. Raise new alarm. key={}, oldID={}, newID={}",
								ChassisConfig.ManoConfig.AlarmConfig.ALARM_CACHE_MAX_MS, keyToCache, put, messageID);
							return doCache(messageBuilder, keyToCache, messageID);
						}
						return false;
					}
				} else {
					return queueWriteEvent.offer(messageBuilder, QUEUE_ACTION_TIME_OUT, TimeUnit.MILLISECONDS);
				}
			} catch (Throwable e) {
				logger.error(e, e);
				return false;
			}
		}
	}

	private static boolean doCache(MessageBuilder messageBuilder, String keyToCache, String messageID) throws InterruptedException {
		boolean offer = queueWriteEvent.offer(messageBuilder, QUEUE_ACTION_TIME_OUT, TimeUnit.MILLISECONDS);
		if (offer) {
			cacheAlarmId.put(keyToCache, messageID);
			mapCacheTime.put(keyToCache, System.currentTimeMillis());
		}
		return offer;
	}

//    private void preprocessing() {
//        if (isCached) {
//            this.cacheAlarmId.put(keyToCache, alarmEventBuilder.getId());
//        }
//
//        this.queueWriteEvent.offer(alarmEventBuilder, ClientThreadPool.TIME_OUT, TimeUnit.MILLISECONDS);
//    }

	public static void stop() {
		logger.debug("stop");
		mapTheadSender.values().forEach(PushException::shutdown);
	}

	static String status() {
		return "Exception monitor status. queueWriteEvent=" + queueWriteEvent.size() +
			", mapCacheTime=" + mapCacheTime.size() +
			", mapCacheAlarm=" + cacheAlarmId.size() +
			", countDrop=" + countDrop.get() +
			", alarmCacheTimeMs=" + ChassisConfig.ManoConfig.AlarmConfig.ALARM_CACHE_MAX_MS +
			", details=" + cacheAlarmId.keySet().stream().map(f -> {
			Long aLong = mapCacheTime.get(f);
			String s = aLong != null ? TimeUtils.miliToString(aLong) : "unknown";
			return f + "=" + s;
		}).collect(Collectors.joining(", ", "[", "]"));
	}

	public static void clearCaching() {
		logger.info("Clear alarm cache. alarmCount={}, timeCount={}", cacheAlarmId.size(), mapCacheTime.size());
		cacheAlarmId.clear();
		mapCacheTime.clear();
	}

	public static void clearQueue() {
		logger.info("Clear alarm queue. queuSize={}", queueWriteEvent.size());
		queueWriteEvent.clear();
	}


}