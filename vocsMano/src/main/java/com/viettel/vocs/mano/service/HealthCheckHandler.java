package com.viettel.vocs.mano.service;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.mano.model.ApplicationCheckList;
import com.viettel.vocs.mano.model.CheckType;
import com.viettel.vocs.mano.model.Report;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpAPI;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.common.file.YamlUtils;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class HealthCheckHandler extends HttpAPI {
	protected static final Logger logger = LogManager.getLogger(HealthCheckHandler.class);
	private static boolean healthCheckBypass = ChassisConfig.HealthcheckConfig.HEALTH_CHECK_BYPASS.get();
	private final AtomicBoolean isShutdown = new AtomicBoolean(false);
	private HttpResponseStatus shutdownStatus = HttpResponseStatus.NOT_FOUND;

	public HealthCheckHandler(Handler<HttpServerContext>  uh) {
		super(uh);
	}

	public static String handleReport(Map<String, List<String>> params) {
		List<Report> checkListReport = ApplicationCheckList.getInstance().getInitialReport();
		return checkListReport == null || checkListReport.isEmpty()
			? "" : parseMapReport(params.containsKey("format"), prepareChecklistReport(checkListReport));
	}

	public static String parseMapReport(boolean isYaml, Map<String, Object> result) {
		String payload;
		try {
			payload = isYaml
				? YamlUtils.objectToPrettyYaml(result)
				: JsonUtils.getEncoder().toJson(result);
		} catch (Exception e) {
			logger.error(e);
			payload = e.getMessage();
		}
		return payload;
	}

	@Override
	public void handle(HttpServerContext ctx) {
		Map<String, List<String>> queryStringParams = ctx.getInParams();
		boolean yaml = queryStringParams.containsKey("format");
		boolean initial = queryStringParams.containsKey("initial");
		boolean bypass = queryStringParams.containsKey("bypass");
		if (bypass) {
			if (logger.isDebugEnabled()) {
				logger.debug("Healthcheck bypass is enabled");
			}
			healthCheckBypass = !healthCheckBypass;
			ctx.send(String.valueOf(healthCheckBypass));
			return;
		}
		if (initial) {
			List<Report> checkListReport = ApplicationCheckList.getInstance().getInitialReport();
			if (checkListReport == null || checkListReport.isEmpty()) {
				ctx.send();
				return;
			}
			Map<String, Object> result = prepareChecklistReport(checkListReport);
			ctx.send(parseMapReport(yaml, result));
			return;
		}
		if (isShutdown.get()) {
			ctx.send("{\"status\":\"TERMINATING\"}", shutdownStatus);
			return;
		}
		if (healthCheckBypass) {
			if (logger.isDebugEnabled()) logger.debug("Healthcheck bypass is enabled");
			ctx.send("{\"status\":\"UP\"}");
			return;
		}
		if (!ApplicationCheckList.getInstance().hasChecker() && userHander != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Not use ApplicationCheckList. Trigger user health check implementation");
			}
			userHander.handle(ctx);
			return;
		}
		ApplicationCheckList.getInstance().getCheckListReport(ctx.getChannelString(), checkListReport -> {
			try {
				List<Report> toVerify = new ArrayList<>();
				Map<String, Object> result = new HashMap<>();
				//add mano id
				result.put("vnfInstanceName", CommonConfig.InstanceInfo.VNF_INSTANCE_NAME.get());
				result.put("vduName", CommonConfig.InstanceInfo.VDU_NAME.get());
				List<Report> initialReport = ApplicationCheckList.getInstance().getInitialReport();
				if (initialReport != null && !initialReport.isEmpty()) {
					for (Report report : initialReport) {
						if (!report.getType().equals(CheckType.CONNECTION)) {
							toVerify.add(report);
						}
					}
					result.put("initial", prepareChecklistReport(initialReport));
				}
				if (checkListReport != null && !checkListReport.isEmpty()) {
					toVerify.addAll(checkListReport);
					result.put("current", prepareChecklistReport(checkListReport));
				}
				if (toVerify.isEmpty() && userHander != null) {
					userHander.handle(ctx);
					return;
				}

				ctx.send(parseMapReport(ctx.getInParams().containsKey("format"), result),
					ApplicationCheckList.getInstance().verify(toVerify)
						? HttpResponseStatus.OK
						: HttpResponseStatus.SERVICE_UNAVAILABLE);
			} catch (Exception ex) {
				logger.error(ex, ex);
			}
		});
	}

	private static Map<String, Object> prepareChecklistReport(List<Report> checkListReport) {
		Map<String, Object> wrap = new HashMap<>();
		Map<String, List<Report>> groupByKey = new HashMap<>();
		Map<String, Pair<Integer, Integer>> count = new HashMap<>();
		Map<String, List<String>> unhealthy = new HashMap<>();
		wrap.put("detail", groupByKey);
		for (Report report : checkListReport) {
			String type = report.getType().toString();
			int oldCount = 0;
			int oldOk = 0;
			if (count.containsKey(type)) {
				oldCount = count.get(type).getRight();
				oldOk = count.get(type).getLeft();
			}
			int newOK = report.isHealthy() ? 1 : 0;
			count.put(type, new ImmutablePair<>(oldOk + newOK, oldCount + 1));
			if (!report.isHealthy()) {
				unhealthy.computeIfAbsent(type, f -> new ArrayList<>()).add(report.getCheckID());
			}
			List<Report> reports = groupByKey.computeIfAbsent(type, f -> new ArrayList<>());
			reports.add(report);
		}
		for (Map.Entry<String, Pair<Integer, Integer>> entry : count.entrySet()) {
			wrap.put(entry.getKey(), entry.getValue().getLeft() + "/" + entry.getValue().getRight());
		}
		if (!unhealthy.isEmpty()) {
			wrap.put("unhealthy", unhealthy);
		}
		return wrap;
	}

	@Override
	public void deleteHandle(HttpServerContext serverCtx) {
		isShutdown.set(true);
		shutdownStatus = HttpResponseStatus.OK;
		logger.error("Health check return {}",shutdownStatus);
		serverCtx.send("{\"status\":\"TERMINATING\"}", shutdownStatus);
	}
}
