package com.viettel.vocs.mano.service;

import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.common.os.thread.JobWithTimeout;
import com.viettel.vocs.common.os.thread.SharedThreadPoolManager;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.handler.http.HttpMessageHandler;
import com.viettel.vocs.microchassis.exception.client.ExceptionMonitor;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmEventBuilder;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmNotification;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes;
import com.viettel.vocs.microchassis.exception.data_model.type.AlarmCode;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

/**
 * @author tiennn18
 */
public class CustomAlarm extends Alarm {
	@Getter
	protected String message;
	@Getter protected final AlarmTypes.PerceivedSeverityType severity;

	public CustomAlarm(AlarmCode alarmCode, String resolveKey, String message, AlarmTypes.PerceivedSeverityType severity) {
		this(alarmCode.name(), resolveKey, message, severity);
	}
	public CustomAlarm(Alarm a, String message, AlarmTypes.PerceivedSeverityType severity) {
		this(a.alarmCode, a.resolveKey, message, severity);
	}
	public CustomAlarm(String alarmCode, String resolveKey, String message, AlarmTypes.PerceivedSeverityType severity) {
		super(alarmCode, resolveKey);
		this.message = message;
		this.severity = severity != null ? severity : AlarmTypes.PerceivedSeverityType.WARNING;
	}

	public void doAlarm(HttpMessageHandler<HttpResponse> handlers) {
		try {
			String cachingKey = buildCachingKey();
			if (this.is(AlarmCode.EXCEPTION)) alarmCode = ChassisConst.Name.APPLICATION_EXCEPTION.name();
			AlarmEventBuilder aeb = makeAeb(cachingKey)
				.withMetadata("fault_details_replacement_mode", "REPLACE")
				.withFaultDetails(message)
				.withAlarmRaisedTime(System.currentTimeMillis())
				.withPerceivedSeverity(severity);

			boolean isCached = needCaching();
			String messageID = aeb.getId();
			if (ExceptionMonitor.captureAlarm(aeb, isCached, cachingKey)) {
				ManoIntegration.mapManoCallBack.put(messageID, Pair.of("alarm", handlers));
				ManoIntegration.mapAlarmSuccess.put(cachingKey, messageID);
				ManoAlarmHelper.addWatch(this);
				if (logger.isDebugEnabled()) {
					AlarmNotification build = aeb.build();
					logger.debug("Send mano alarm. alarmId={}, {}, isCache={}, json={}",
						build.getId(), this, isCached, JsonUtils.getEncoder().toJson(build));
				} else logger.info("Send mano alarm. alarmId={}, {}, isCache={}", messageID, this, isCached);
			} else logger.info("Queue alarm drop. key={}, messageId={}", cachingKey, messageID);
		} catch (Throwable ex) {
			logger.error(ex, ex);
		}
	}

	@Override
	public String toString() {
		return super.toString() + String.format(", message=%s, serverity=%s", message, severity);
	}
	public void alarm() {
		alarm(null);
	}
	public void alarm(HttpMessageHandler<HttpResponse> handler) {
		if (!(ManoIntegration.isAlarm())) {
			if (logger.isDebugEnabled()) {
				logger.debug("mano.enable is {}. mano.alarm is {}. not send alarm", ManoIntegration.isEnable(), ManoIntegration.isAlarm());
			}
			return;
		}
		if (isAlarmIgnored()) {
			logger.error("Alarm code {} is ignored.", alarmCode);
			return;
		}
		String cachingKey = buildCachingKey();
		if (ExceptionMonitor.cacheAlarmId.containsKey(cachingKey)) {
			if (logger.isDebugEnabled())
				logger.debug("[MANO alarm is cached. Ignore]{{}, cachingKey={}}", this, cachingKey);
			return;
		}
		JobWithTimeout alarmJob = new JobWithTimeout() {
			@Override
			public String getName() {
				return "Mano alarm " + alarmCode + "_" + resolveKey;
			}

			@Override
			public void run() {
				doAlarm(handler);
			}
		};
		if (ManoIntegration.isNotified()) {
			if (ChassisConfig.ManoConfig.MANO_USE_ASYNC_REQUEST.get() && !mustSync()) {
				SharedThreadPoolManager.executeAsync(alarmJob);
			} else {
				alarmJob.run();
			}
		} else ManoIntegration.addJobToQueue(alarmJob);
	}
}
