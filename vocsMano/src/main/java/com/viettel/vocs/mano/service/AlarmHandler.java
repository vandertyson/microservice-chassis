package com.viettel.vocs.mano.service;

import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpAPI;
import com.viettel.vocs.microchassis.codec.handler.http.HttpMessageHandler;
import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.exception.client.ExceptionMonitor;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class AlarmHandler extends HttpAPI {

	@Override
	public void handle(HttpServerContext ctx) {
		Map<String, List<String>> queryStringParams = ctx.getInParams();
		boolean toggle = queryStringParams.containsKey("toggle");
		boolean custom = queryStringParams.containsKey("custom");
		boolean export = queryStringParams.containsKey("export");
		boolean stat = queryStringParams.containsKey("stat");
		boolean clear = queryStringParams.containsKey("clear");
		if (stat) {
			ctx.send(ExceptionMonitor.status());
			return;
		}
		if (export) {
			Map<String, String> result = new HashMap<>();
			Map<String, String> cacheAlarmId = ExceptionMonitor.cacheAlarmId;
			Map<String, Long> mapCacheTime = ExceptionMonitor.mapCacheTime;
			for (Map.Entry<String, String> next : cacheAlarmId.entrySet()) {
				String keyResolve = next.getKey();
				Long time = mapCacheTime.get(keyResolve);
				result.put(keyResolve, TimeUtils.miliToString(time));
			}
			ctx.send(JsonUtils.getEncoder().toJson(result).getBytes());
			return;
		}
		if (toggle) {
			Map<String, List<String>> inParams = ctx.getInParams();
			List<String> key = inParams.get("key");
			String message = "";
			if (key != null && !key.isEmpty()) {
				Set<String> keys = Alarm.noCachingAlarmCode;
				synchronized (keys) {
					String s = key.get(0);
					if (keys.contains(s)) {
						keys.remove(s);
						message = "Mano alarm is enabled for keyword " + s;
					} else {
						keys.add(s);
						message = "Mano alarm is disabled for keyword " + s;
					}
				}
			} else {
				boolean isAlarmed = ManoIntegration.toggleAlarm();
				message = isAlarmed ? "Mano alarm is enabled" : "Mano alarm is disabled";
			}
			ctx.send(message);
			return;
		}
		if (custom) {
			try {
				String json = ctx.getInMsg().toString(CharsetUtil.UTF_8);
				CustomAlarm newAlarm = JsonUtils.getDecoder().fromJson(json, CustomAlarm.class);
				boolean isResolve = (newAlarm.message == null || newAlarm.message.isEmpty()) && (newAlarm.severity == null);
				logger.info("Mano custom alarm. code={}, key={}, message={}, isResolve={}", newAlarm.getAlarmCode(), newAlarm.getResolveKey(), newAlarm.message, isResolve);
				HttpMessageHandler<HttpResponse> handler = new HttpMessageHandler<>() {
					@Override
					public void handle(HttpResponse msg) {
						try {
							if (logger.isDebugEnabled()) {
								logger.debug("Mano custom alarm response. status={}, headers={}, content={}", msg.status(), msg.headers(), msg.content().toString(CharsetUtil.UTF_8));
							}
							ctx.send(msg.replicate(), (GenericFutureListener< ChannelFuture >[])null);
						} catch (Exception ex) {
							logger.error(ex, ex);
						}
					}

					@Override
					public void timeoutHandle(String requestID) {
						try {
							if (logger.isDebugEnabled()) {
								logger.debug("Mano custom alarm timeout. alarmID={}", requestID);
							}
							ctx.send(requestID.getBytes(), HttpResponseStatus.GATEWAY_TIMEOUT);
						} catch (Exception ex) {
							logger.error(ex, ex);
						}
					}
				};
				if (!isResolve) {
					newAlarm.doAlarm(handler);
				} else {
					newAlarm.resolve(handler);
				}
			} catch (Exception ex) {
				logger.error(ex, ex);
				try {
					ctx.send(ex.getMessage(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
				} catch (Exception e) {
					logger.error(ex, ex);
				}
			}
			return;
		}
		if (clear) {
			try {
				List<String> type = queryStringParams.get("type");
				if (type == null) {
					ExceptionMonitor.clearCaching();
					ExceptionMonitor.clearQueue();
					ctx.send(ExceptionMonitor.status());
					return;
				}
				if (type.contains("queue")) {
					ExceptionMonitor.clearQueue();
					ctx.send(ExceptionMonitor.status());
					return;
				}
				if (type.equals("cache")) {
					ExceptionMonitor.clearCaching();
					ctx.send(ExceptionMonitor.status());
					return;
				}
			} catch (Exception ex) {
				ctx.send(ex.getMessage(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
			}
			return;
		}
		ctx.send(ExceptionMonitor.status());
	}


}
