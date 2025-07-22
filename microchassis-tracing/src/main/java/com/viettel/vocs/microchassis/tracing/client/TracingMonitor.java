package com.viettel.vocs.microchassis.tracing.client;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.IDfy;
import com.viettel.vocs.common.config.tracing.JaegerConfiguration;
import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.common.file.YamlUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisThreadManager;
import com.viettel.vocs.microchassis.tracing.TracingConfiguration;
import com.viettel.vocs.microchassis.tracing.model.TraceSessionHistory;
import com.viettel.vocs.microchassis.tracing.model.TracingData;
import com.viettel.vocs.microchassis.tracing.model.TracingDataImpl;
import com.viettel.vocs.microchassis.tracing.utils.TracingUtils;
import io.jaegertracing.internal.JaegerTracer;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TracingMonitor {
	private static final Logger logger = LogManager.getLogger(TracingMonitor.class);
	private static volatile TracingMonitor instance;
	private TracingConfiguration parameter = new TracingConfiguration();
	private JaegerTracer tracer;
	private String configPath;
	private Map<String, TraceSessionHistory> history = new ConcurrentHashMap<>();
	private String serviceName;
	private int maxTracingSize = ChassisConfig.TracingConfig.DEFAULT_MAX_TRACING_OBJ.getInt();
	private int defaultIdleSec = ChassisConfig.TracingConfig.DEFAULT_MAX_TRACING_OBJ.getInt();

	private TracingMonitor() {
		this.tracer = null;
		overideServiceName(null);
	}

	public static String getTracingConfig() {
		return JsonUtils.getEncoder().toJson(Map.of(
			"config", getInstance().getParameter(),
			"history", getInstance().getHistory()
				.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
					Map<String, String> data = new HashMap<>();
					data.put("start", TimeUtils.miliToString(e.getValue().getStart()));
					if (e.getValue().getLastTrace() > 0)
						data.put("last_trace", TimeUtils.miliToString(e.getValue().getLastTrace()));
					return data;
				}))));
	}

	public void setMaxTracingSize(int maxTracingSize) {
		this.maxTracingSize = maxTracingSize;
	}

	public int getMaxTracingSize() {
		return maxTracingSize;
	}

	public final Map<String, TraceSessionHistory> getHistory() {
		return history;
	}

	public static TracingMonitor getInstance() {
		if (instance == null) {
			synchronized (TracingMonitor.class) {
				if (instance == null) {
					instance = new TracingMonitor();
				}
			}
		}

		return instance;
	}

	public TracingMonitor withServiceName(String serviceName) {
		overideServiceName(serviceName);
		return this;
	}

	private void overideServiceName(String serviceName) {
		String vnf_instance_name = CommonConfig.InstanceInfo.VNF_INSTANCE_NAME.get();
		if (vnf_instance_name != null && !vnf_instance_name.isEmpty()) {
			this.serviceName = vnf_instance_name;
			return;
		}
		if (serviceName != null && !serviceName.isEmpty()) {
			this.serviceName = serviceName;
			return;
		}
		try {
			this.serviceName = InetAddress.getLocalHost().getHostName();
		} catch (Exception ex) {
			logger.error(ex);
			this.serviceName = IDfy.generateNewId();
		}
	}

	public TracingMonitor withConfigPath(String ymlPath) {
		this.configPath = ymlPath;
		return this;
	}

	public void startTracing() {
		logger.info("Start tracing monitor. configPath={}, serviceName={}", configPath, serviceName);
		if (configPath != null && !configPath.isEmpty()) {
			try {
				parameter = YamlUtils.objectFromYaml(TracingConfiguration.class, configPath);
				if (parameter.getJaegerConfiguration() != null) {
					setJaegerConfig(parameter.getJaegerConfiguration());
				}
				String parsed = YamlUtils.objectToPrettyYaml(parameter);
				logger.info("Loaded tracing configuration from file\n{}", parsed);
			} catch (Exception ex) {
				logger.error(ex, ex);
				String parsed = YamlUtils.objectToPrettyYaml(parameter);
				logger.info("Error loading tracing configuration. Use default configuration\n{}", parsed);
			}
		} else {
			String parsed = YamlUtils.objectToPrettyYaml(parameter);
			logger.info("No tracing config specified. Use default configuration\n{}", parsed);
		}
		try {
			this.tracer = TracingUtils.initJaegerTracer(this.serviceName);
		} catch (Exception ex) {
			logger.error(ex, ex);
		}
		ThreadManager.JobMonitor.scheduleJob(new ThreadManager.JobMonitor.Job() {
			@Override
			public String getId() {
				return "Tracing monitor watcher";
			}

			@Override
			public Runnable getJob() {
				return () -> {
					Iterator<String> iterator = parameter.getListMsisdn().iterator();
					while (iterator.hasNext()) {
						String s = iterator.next();
						TraceSessionHistory traceSessionHistory = history.get(s);
						if (traceSessionHistory != null) {
							long maxWaitMs = TimeUnit.SECONDS.toMillis(traceSessionHistory.getIdleSec());
							try {
								if (System.currentTimeMillis() - traceSessionHistory.getStart() > maxWaitMs) {
									iterator.remove();
									disableTracingForMsisdn(s);
								}
							} catch (Exception ex) {
								logger.error(ex, ex);
								break;
							}
						}
					}
					Iterator<String> iterator1 = parameter.getListSessionId().iterator();
					while (iterator1.hasNext()) {
						String s = iterator1.next();
						TraceSessionHistory traceSessionHistory = history.get(s);
						if (traceSessionHistory != null) {
							long maxWaitMs = TimeUnit.SECONDS.toMillis(traceSessionHistory.getIdleSec());
							try {
								if (System.currentTimeMillis() - traceSessionHistory.getStart() > maxWaitMs) {
									iterator1.remove();
									disableTracingForSession(s);
								}
							} catch (Exception ex) {
								logger.error(ex, ex);
								break;
							}
						}
					}
					if (parameter.getListSessionId().isEmpty() && parameter.getListMsisdn().isEmpty()) {
						parameter.setEnableTracing(false);
					}
				};
			}

			@Override
			public int getInitialDelay() {
				return 0;
			}

			@Override
			public int getInterval() {
				return ChassisConfig.TracingConfig.tracingIdleIntervalSec.getInt();
			}

			@Override
			public TimeUnit getTimeUnit() {
				return TimeUnit.SECONDS;
			}
		});
	}

	private void disableTracingForMsisdn(String s) {
		disableTracing(s, "End of tracing for msisdn. msisdn={}, durationMs={}, startTrace={}, ");
	}

	private void disableTracingForSession(String s) {
		disableTracing(s, "End of tracing for session. sessionID={}, durationMs={}, startTrace={}, ");
	}

	private void disableTracing(String s, String x) {
		TraceSessionHistory remove1 = history.remove(s);
		if (remove1 != null) {
			logger.info(x +
					"lastTrace={{}}}",
				s,
				System.currentTimeMillis() - remove1.getStart()
				, new Date(remove1.getStart()).toLocaleString()
				, new Date(remove1.getLastTrace()).toLocaleString());
		}
	}


	public void stopTracing() {
		if (logger.isDebugEnabled()) {
			logger.debug("stopTracing");
		}
		if (tracer != null) {
			tracer.close();
		}
	}

	public TracingConfiguration getParameter() {
		return parameter;
	}

	public synchronized Pair<HttpResponseStatus, String> updateParameter(TracingConfiguration parameterReq) {
		boolean msisdnConfigured = parameterReq.getListMsisdn() != null
			&& !parameterReq.getListMsisdn().isEmpty();
		boolean sessionIdConfigured = parameterReq.getListSessionId() != null
			&& !parameterReq.getListSessionId().isEmpty();
		String message = "Tracing configured.";
		Long idleSec = 600L;
		if (parameterReq.getMaxTracingIdleSec() != null && parameterReq.getMaxTracingIdleSec() > 0) {
			idleSec = parameterReq.getMaxTracingIdleSec();
			parameter.setMaxTracingIdleSec(idleSec);
		}
		if (msisdnConfigured) {
			if (parameterReq.getListMsisdn().size() > maxTracingSize) {
				message = "Invalid configuration. Allow maximum " + maxTracingSize + " msisdn";
				logger.error(message);
				return new ImmutablePair<>(HttpResponseStatus.FORBIDDEN, message);
			}
			if (parameter.getListMsisdn().size() >= maxTracingSize) {
				message = "Maximum tracing msisdn reached. Configuration is not allowed";
				logger.error(message);
				return new ImmutablePair<>(HttpResponseStatus.SERVICE_UNAVAILABLE, message);
			}
			configureTracingList(parameterReq.getListMsisdn(), this.parameter.getListMsisdn(), parameterReq, "msisdn", idleSec);
		}
		if (sessionIdConfigured) {
			if (parameterReq.getListMsisdn().size() > maxTracingSize) {
				message = "Invalid configuration. Allow maximum " + maxTracingSize + " session id";
				logger.error(message);
				return new ImmutablePair<>(HttpResponseStatus.FORBIDDEN, message);
			}
			if (parameter.getListSessionId().size() >= maxTracingSize) {
				message = "Maximum tracing session id reached. Configuration is not allowed";
				logger.error(message);
				return new ImmutablePair<>(HttpResponseStatus.SERVICE_UNAVAILABLE, message);
			}
			configureTracingList(parameterReq.getListSessionId(), this.parameter.getListSessionId(), parameterReq, "session", idleSec);
		}
		parameter.setEnableTracing(parameterReq.isEnableTracing());
		if (parameter.getListSessionId().isEmpty() && parameter.getListMsisdn().isEmpty()) {
			parameter.setEnableTracing(false);
		}
		return new ImmutablePair<>(HttpResponseStatus.OK, message);
	}

	private void configureTracingList(Set<String> parameterReq, Set<String> parameter, TracingConfiguration parameterReq1, String type, long idleSec) {
		Set<String> affected = new HashSet<>();
		Set<String> listData = parameterReq;
		Set<String> listCurrent = parameter;
		boolean enable = parameterReq1.isEnableTracing();
		for (String s : listData) {
			boolean affect;
			if (!enable) {
				Iterator<String> iterator = listCurrent.iterator();
				while (iterator.hasNext()) {
					String next = iterator.next();
					if (Objects.equals(next, s)) {
						iterator.remove();
						affected.add(s);
						TraceSessionHistory remove = history.remove(s);
						if (remove != null) {
							logger.info("Disable trace. key={}, start={}, last_trace={}"
								, s
								, TimeUtils.miliToString(remove.getStart())
								, TimeUtils.miliToString(remove.getLastTrace()));
						}
					}
				}
			} else {
				affect = listCurrent.add(s);
				if (affect) {
					TraceSessionHistory traceSessionHistory = new TraceSessionHistory();
					traceSessionHistory.setStart(System.currentTimeMillis());
					traceSessionHistory.setIdleSec(idleSec);
					history.put(s, traceSessionHistory);
					logger.info("New trace created. key={}, start={}, end={}"
						, s
						, TimeUtils.miliToString(traceSessionHistory.getStart())
						, TimeUtils.miliToString(traceSessionHistory.getStart() + TimeUnit.SECONDS.toMillis(idleSec))
					);
				} else {
					TraceSessionHistory traceSessionHistory = history.get(s);
					if (traceSessionHistory != null) {
						long start = traceSessionHistory.getStart();
						traceSessionHistory.setStart(System.currentTimeMillis());
						traceSessionHistory.setIdleSec(idleSec);
						logger.info("Extend trace. key={}, last_start={}, new_start={}, new_end={}"
							, s
							, TimeUtils.miliToString(start)
							, TimeUtils.miliToString(traceSessionHistory.getStart())
							, TimeUtils.miliToString(traceSessionHistory.getStart() + TimeUnit.SECONDS.toMillis(idleSec))
						);
					}
				}
				affected.add(s);
			}
		}
		logger.info("Tracing {} configured. action={}, affected={}, current={}",
			type, enable ? "enable" : "disable", affected, listCurrent);
	}

	public Tracer getTracer() {
		return tracer;
	}

	private boolean traceByMsisdn(String msisdn) {
		if (logger.isDebugEnabled()) {
			logger.debug("traceByMsisdn, msisdn={}", msisdn);
		}
		if (msisdn == null || msisdn.isBlank()) {
			return false;
		}
		Set<String> listMsisdn = this.parameter.getListMsisdn();
		if (listMsisdn == null || listMsisdn.isEmpty()) {
			return false;
		}

		return listMsisdn.contains(msisdn);
	}

	private boolean traceBySessionId(String sessionId) {
		if (logger.isDebugEnabled()) {
			logger.debug("traceBySessionId, sessionID={}", sessionId);
		}

		if (sessionId == null || sessionId.isBlank()) {
			return false;
		}
		Set<String> listSessionId = this.parameter.getListSessionId();
		if (listSessionId == null || listSessionId.isEmpty()) {
			return false;
		}

		return listSessionId.contains(sessionId);
	}

	public boolean checkTracing(TracingData data) {
		if (logger.isDebugEnabled()) {
			logger.debug("checkTracing with data {}", data);
		}
		if (data == null || data.getMsisdn() == null) {
			return false;
		}
		return checkTracing(data.getMsisdn(), data.getSessionID());
	}

	public boolean checkTracing(String msisdn) {
		if (logger.isDebugEnabled()) {
			logger.debug("checkTracing");
		}
		return TracingMonitor.getInstance().traceByMsisdn(msisdn);
	}

	public boolean checkTracing(String msisdn, String sessionId) {
		return TracingMonitor.getInstance().traceByMsisdn(msisdn)
			|| TracingMonitor.getInstance().traceBySessionId(sessionId);
	}

	public boolean checkTracing(Object msisdn, Object sessionId) {
		if (msisdn == null && sessionId == null) {
			return false;
		}
		if (msisdn != null) {
			return TracingMonitor.getInstance().traceByMsisdn(msisdn.toString());
		}
		if (sessionId != null) {
			return TracingMonitor.getInstance().traceBySessionId(sessionId.toString());
		}
		return false;
	}

	public boolean isTraceEnable() {
		return this.parameter.isEnableTracing();
	}

	public TracingData create() {
		TracingDataImpl tracingData = createNewTraceInstance();
		return tracingData;
	}

	private TracingDataImpl createNewTraceInstance() {
		TracingDataImpl tracingData = new TracingDataImpl(ChassisThreadManager.tracingPool);
		tracingData.setTraceListener(hand);
		tracingData.setTracer(tracer);
		return tracingData;
	}

	TraceHandler hand = new TraceHandler() {
		@Override
		public void onSessionTrace(String msisdn) {
			updateTraceHistory(msisdn);
		}

		@Override
		public void onMsisdnTrace(String sessionId) {
			updateTraceHistory(sessionId);
		}

		private void updateTraceHistory(String msisdn) {
			TraceSessionHistory traceSessionHistory = history.get(msisdn);
			if (traceSessionHistory != null) {
				traceSessionHistory.setLastTrace(System.currentTimeMillis());
			}
		}
	};

	public TracingData create(String parentContextId) {
		return getTracingData(TracingUtils.extractSpanContext(parentContextId));
	}

	private TracingDataImpl getTracingData(SpanContext parentMsg) { // allow null
		TracingDataImpl tracingData = createNewTraceInstance();
		if (parentMsg != null) tracingData.setParentSpanContext(parentMsg);
		return tracingData;
	}

	private void setJaegerConfig(JaegerConfiguration config) {
		if (config.jaegerServiceName != null) {
			System.setProperty("JAEGER_SERVICE_NAME", config.jaegerServiceName);
		}
		if (config.jaegerAgentHost != null) {
			System.setProperty("JAEGER_AGENT_HOST", config.jaegerAgentHost);
		}
		if (config.jaegerAgentPort != null) {
			System.setProperty("JAEGER_AGENT_PORT", config.jaegerAgentPort);
		}
		if (config.jaegerEndpoint != null) {
			System.setProperty("JAEGER_ENDPOINT", config.jaegerEndpoint);
		}
		if (config.jaegerAuthToken != null) {
			System.setProperty("JAEGER_AUTH_TOKEN", config.jaegerAuthToken);
		}
		if (config.jaegerUser != null) {
			System.setProperty("JAEGER_USER", config.jaegerUser);
		}
		if (config.jaegerPassword != null) {
			System.setProperty("JAEGER_PASSWORD", config.jaegerPassword);
		}
		if (config.jaegerPropagation != null) {
			System.setProperty("JAEGER_PROPAGATION", config.jaegerPropagation);
		}
		if (config.jaegerReporterLogSpans != null) {
			System.setProperty("JAEGER_REPORTER_LOG_SPANS", config.jaegerReporterLogSpans);
		}
		if (config.jaegerReporterMaxQueueSize != null) {
			System.setProperty("JAEGER_REPORTER_MAX_QUEUE_SIZE", config.jaegerReporterMaxQueueSize);
		}
		if (config.jaegerReporterFlushInterval != null) {
			System.setProperty("JAEGER_REPORTER_FLUSH_INTERVAL", config.jaegerReporterFlushInterval);
		}
		if (config.jaegerSamplerType != null) {
			System.setProperty("JAEGER_SAMPLER_TYPE", config.jaegerSamplerType);
		}
		if (config.jaegerSamplerParam != null) {
			System.setProperty("JAEGER_SAMPLER_PARAM", config.jaegerSamplerParam);
		}
		if (config.jaegerSamplerManagerHostPort != null) {
			System.setProperty("JAEGER_SAMPLER_MANAGER_HOST_PORT", config.jaegerSamplerManagerHostPort);
		}
		if (config.jaegerTags != null) {
			System.setProperty("JAEGER_TAGS", config.jaegerTags);
		}
	}

	public static void main(String[] args) throws InterruptedException {
		HashSet<String> x = new HashSet<>();
		x.add("1");
		x.add("2");
		try {
			x.removeIf("1"::equals);
			System.out.println("1");
		} catch (Throwable e) {
			e.printStackTrace();
			System.out.println("ok");
		}

		Set<String> a = new HashSet<>();
		a.add("1");
		a.add("2");
		System.out.println(a.add("2"));
		System.out.println(a.remove("3"));
		System.out.println(a.remove("1"));
		TracingConfiguration conf = new TracingConfiguration();
		System.out.println(conf);
	}

	@Override
	public String toString() {
		return String.format("Tracing monitor status. parameter=%s, history=%d, pool={%s}",
			parameter, history.size(), ChassisThreadManager.tracingPool.report());
	}
}