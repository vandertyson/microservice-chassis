package com.viettel.vocs.mano.service;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.microchassis.codec.handler.http.HttpMessageHandler;
import com.viettel.vocs.common.IDfy;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.os.thread.JobWithTimeout;
import com.viettel.vocs.common.os.thread.SharedThreadPoolManager;
import com.viettel.vocs.microchassis.exception.client.ExceptionMonitor;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmEventBuilder;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes;
import com.viettel.vocs.microchassis.exception.data_model.type.AlarmCode;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;


/**
 * @author tiennn18
 */
public class Alarm {
	public static final Set<String> ignoredAlarmCodeKeywords = Collections.synchronizedSet(new CopyOnWriteArraySet<>());
	protected static final Logger logger = LogManager.getLogger(Alarm.class);
	static final Set<String> noCachingAlarmCode = new CopyOnWriteArraySet<>();
	public CustomAlarm sendCritical(String detail) {
		CustomAlarm alarm = new CustomAlarm(this, detail, AlarmTypes.PerceivedSeverityType.CRITICAL);
		alarm.alarm();
		return alarm;
	}
	public CustomAlarm sendWaring(String detail) {
		CustomAlarm alarm = new CustomAlarm(this, detail, AlarmTypes.PerceivedSeverityType.WARNING);
		alarm.alarm();
		return alarm;
	}
	public CustomAlarm sendMinor(String detail) {
		CustomAlarm alarm = new CustomAlarm(this, detail, AlarmTypes.PerceivedSeverityType.MINOR);
		alarm.alarm();
		return alarm;
	}
	public CustomAlarm senMajor(String detail) {
		CustomAlarm alarm = new CustomAlarm(this, detail, AlarmTypes.PerceivedSeverityType.MAJOR);
		alarm.alarm();
		return alarm;
	}
	public static void addNotCachingErrorCode(String alarmCode) {
		if (!noCachingAlarmCode.contains(alarmCode)) {
			noCachingAlarmCode.add(alarmCode);
			logger.info("[Caching alarm code {} added]{all={}}", alarmCode, Alarm.noCachingAlarmCode);
		}
	}

	public static void removeNotCachingErrorCode(String alarmCode) {
		if (noCachingAlarmCode.removeIf(f -> f.equals(alarmCode))) {
			logger.info("[Caching alarm code {} removed]{all={}}", alarmCode, noCachingAlarmCode);
		}
	}

	@Getter
	protected String alarmCode;
	@Getter
	protected String resolveKey;

	public Alarm(String alarmCode, String resolveKey) {
		this.alarmCode = alarmCode;
		this.resolveKey = resolveKey;
	}

	public Alarm(AlarmCode alarmCode, String resolveKey) {
		this.alarmCode = alarmCode.name();
		this.resolveKey = resolveKey;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Alarm)) return false;
		Alarm o = (Alarm) obj;
		return Objects.equals(alarmCode, o.alarmCode) && Objects.equals(resolveKey, o.resolveKey);
	}

	protected AlarmEventBuilder makeAeb(String cachingKey) {
		return new AlarmEventBuilder()
			.withId(IDfy.generateNewId())
			.withVnfcIds(List.of(CommonConfig.InstanceInfo.VNFC_ID.get()))
			.withVnfcNames(List.of(CommonConfig.InstanceInfo.VNFC_NAME.get()))
			.withManagedObjectId(CommonConfig.InstanceInfo.VNF_INSTANCE_ID.get())
			.withManagedObjectName(CommonConfig.InstanceInfo.VNF_INSTANCE_NAME.get())
			.withAlarmType(AlarmTypes.ManagedObjectType.CNF_OBJECT)
			.withType(ManoIntegration.ALARM_TYPE)
			.withMetadata("errorCode", alarmCode)
			.withMetadata("keyResolve", cachingKey);
	}

	public void resolve() {
		resolve(null);
	}

	public void resolve(HttpMessageHandler<HttpResponse> handler) {
		if (!(ManoIntegration.isAlarm())) {
			if (logger.isDebugEnabled()) {
				logger.debug("mano.enable is {}. mano.alarm is {}. not send alarm", ManoIntegration.isEnable(), ManoIntegration.isAlarm());
			}
			return;
		}
		if (needCaching() && !needResolve()) {
			if (logger.isDebugEnabled()) {
				logger.debug("[Mano no need resolve]{{}}", toString());
			}
			return;
		}
		JobWithTimeout resolveJob = new JobWithTimeout() {
			@Override
			public String getName() {
				return "Mano resolve " + alarmCode + "_" + getResolveKey();
			}

			@Override
			public void run() {
				try {
					//Smell. Need refactor. Pause to watch for alarm
					if (mustSync()) {
						TimeUtils.waitSafeMili(ChassisConfig.MetricConfig.magicResolveWaitMs.get());
					}
					if (needCaching() && !needResolve()) {
						if (logger.isDebugEnabled()) {
							logger.debug("[Mano no need resolve]{{}}", toString());
						}
						return;
					}
					String cachingKey = buildCachingKey();
					AlarmEventBuilder aeb = makeAeb(cachingKey)
						.withNotificationType(AlarmTypes.NotificationType.ALARM_CLEARED_NOTIFICATION)
						.withAlarmClearedTime(System.currentTimeMillis());
					String remove = ManoIntegration.mapAlarmSuccess.remove(cachingKey);
					if (remove != null) {
						CompletableFuture<Boolean> waitAlarmSuccess = ManoIntegration.mapMessageID.remove(remove);
						if (waitAlarmSuccess != null && !waitAlarmSuccess.isDone() && !waitAlarmSuccess.isCancelled()) {
							try {
								waitAlarmSuccess.get(ChassisConfig.ManoConfig.AlarmConfig.ALARM_RESPONSE_MAX_WAIT_SEC.get(), TimeUnit.SECONDS);
							} catch (Throwable ex) {
								logger.error("[Error waiting alarm response. Send resolve anyway]{alarmID={}, cachingKey={}}"
									, remove, cachingKey);
							}
						}
					}
					if (ExceptionMonitor.captureAlarm(aeb, false, cachingKey)) {
						ManoIntegration.mapManoCallBack.put(aeb.getId(), Pair.of("resolve", handler));
						ManoAlarmHelper.removeWatch(Alarm.this);
						if (logger.isDebugEnabled()) {
							logger.info("Send mano resolve. alarmId={}, {}, json={}",
								aeb.getId(), toString(), JsonUtils.getEncoder().toJson(aeb.build()));
						} else logger.info("Send mano resolve. alarmId={}, {}", aeb.getId(), toString());
					} else logger.info("Queue resolve drop. key={}, messageId={}", cachingKey, aeb.getId());
				} catch (Throwable ex) {
					logger.error(ex, ex);
				}
			}
		};
		if (ManoIntegration.isNotified()) {
			if (ChassisConfig.ManoConfig.MANO_USE_ASYNC_REQUEST.get() && !mustSync()) {
				SharedThreadPoolManager.executeAsync(resolveJob);
			} else {
				resolveJob.run();
			}
		} else {
			ManoIntegration.addJobToQueue(resolveJob);
		}
	}


	public boolean mustSync() {
		return isOneOf(AlarmCode.INTERNAL_CONNECTION, AlarmCode.INTERNAL_SERVICE);
	}

	public boolean needResolve() {
		return ExceptionMonitor.cacheAlarmId.containsKey(buildCachingKey());
	}

	public boolean needCaching() {
		return noCachingAlarmCode.isEmpty() || !noCachingAlarmCode.contains(alarmCode);
	}

	public boolean isAlarmIgnored() {
		return !ignoredAlarmCodeKeywords.isEmpty() && ignoredAlarmCodeKeywords.stream().anyMatch(ignoredKeyword -> alarmCode.toLowerCase().contains(ignoredKeyword.toLowerCase()));
	}


	@Override
	public String toString() {
		return String.format("code=%s, keyResolve=%s", alarmCode, resolveKey);
	}

	public String getAlarmCode() {
		return alarmCode;
	}

	public String getResolveKey() {
		return resolveKey;
	}

	public boolean is(AlarmCode code) {
		return isOneOf(code);
	}
	public boolean isOneOf(AlarmCode... listCodes) {
		return alarmCode != null && Arrays.stream(listCodes).anyMatch(code -> code != null && code.toString().equals(alarmCode));
	}

	String buildCachingKey() {
		return ManoAlarmHelper.buildCachingKey(alarmCode, resolveKey);
	}
}
