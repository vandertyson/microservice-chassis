package com.viettel.vocs.microchassis.base;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.CommonConfig.JavaConfig;
import com.viettel.vocs.common.CommonConfig.QuartzConfig;
import com.viettel.vocs.common.CommonConfig.RateLimiterConfig;
import com.viettel.vocs.common.CommonConfig.ThreadPoolExecutorConfig;
import com.viettel.vocs.common.CommonConst;
import com.viettel.vocs.common.config.value.*;
import com.viettel.vocs.common.ocs.PodLocation;
import com.viettel.vocs.common.os.RateClock;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.viettel.vocs.microchassis.base.ChassisConst.*;

/**
 * @author tiennn18
 */
public class ChassisConfig {

	public static class PerformanceConfig { // greatly impact to performance

		public static final ThreadPoolExecutorConfig H_CLIENT_HANDLEPOOL_CONFIG = new ThreadPoolExecutorConfig("client.http.handlepool", "http_c-%d");
		public static final ThreadPoolExecutorConfig H_SERVER_HANDLEPOOL_CONFIG = new ThreadPoolExecutorConfig("server.http.handlepool", "http_c-%d");
		public static final ThreadPoolExecutorConfig T_CLIENT_HANDLEPOOL_CONFIG = new ThreadPoolExecutorConfig("client.tcp.handlepool", "tcp_c-%d");
		public static final ThreadPoolExecutorConfig T_SERVER_HANDLEPOOL_CONFIG = new ThreadPoolExecutorConfig("server.tcp.handlepool", "tcp_s-%d");
		public static final IntegerConfig BUSINESS_HANDLE_THREAD = new IntegerConfig("BUSINESS_THREADS", "business.threads", JavaConfig.TOTAL_THREADS.get());
		public static final StringConfig JVM_IMPORTER_CONFIG = new StringConfig("JVM_IMPORTER_CONFIG", "chassis.metric.jvm.importer", "otel");
		public static final BooleanConfig performanceChassis = new BooleanConfig("CHASSIS_PERFORMANCE_STATISTIC", false);

		public static class WarmupConfig {
			public static final IntegerConfig TCP_WARMUP_SIZE = new IntegerConfig(null, "TCP_WARMUP_SIZE", 4096);
			public static final IntegerConfig TCP_WARMUP_COUNT = new IntegerConfig(null, "TCP_WARMUP_COUNT", 100000);
			public static final ListIntegerConfig WARMUP_STEPS = new ListIntegerConfig(null, WARMUP_TPS_STEPS, 1L, 2L, 4L, 8L);
			public static final BooleanConfig WARMUP_DEFAULT = new BooleanConfig(CLIENT_WARMUP_ENABLE, false);
		}
	}

	public static class LBRConfig {
		public static final DerivativeConfig<String> DEFAULT_LBR_PATH = CommonConfig.JavaConfig.makeDeriFilePath("LBR.yml");
		public static final StringConfig assignMode = new StringConfig(null, "assignMode", LBRConst.ASSIGN_FILL);
		public static final IntegerConfig POLLING_INTERVAL_MS = new IntegerConfig(null, "lb.polling.intervalms", 2000);
		public static final IntegerConfig peerDropToAutonomous = new IntegerConfig(null, "lb.autonomous.peerDropMax", 2);
	}

	public static class ConnectionConfig {
		public static final StringConfig DEFAULT_CONNECTION_DIR = new StringConfig("DEFAULT_CONNECTION_DIR", "DEFAULT_CONNECTION_DIR", JavaConfig.CONFIG_FOLDER);
		public static final StringConfig DEFAULT_BASE_CONNECTION_DIR = new StringConfig("DEFAULT_BASE_CONNECTION_DIR", "DEFAULT_BASE_CONNECTION_DIR", CommonConfig.JavaConfig.makeDeriFilePath(JavaConfig.CONNECTION_FOLDER, "microchassis"));
		public static final StringConfig DEFAULT_CONNECTION_FILENAME = new StringConfig("DEFAULT_CONNECTION_FILENAME","DEFAULT_CONNECTION_FILENAME", "connection.yml");
		public static final DerivativeConfig<String> DEFAULT_BASE_CONNECTION_PATH = CommonConfig.JavaConfig.makeDeriFilePath(DEFAULT_BASE_CONNECTION_DIR, DEFAULT_CONNECTION_FILENAME);
		public static final DerivativeConfig<String> DEFAULT_CONNECTION_PATH = CommonConfig.JavaConfig.makeDeriFilePath(DEFAULT_CONNECTION_DIR, DEFAULT_CONNECTION_FILENAME);
		public static final IntegerConfig NETTY_SO_BACK_LOG = new IntegerConfig(null, BACKLOG, 0);
		public static final IntegerConfig TimeOUTCONNECTMS = new IntegerConfig(null, TIMEOUTCONNECTMS, 3_000);
		public static final IntegerConfig MaxCHANNELWRITABLEMS = new IntegerConfig(null, MAXCHANNELWRITABLEMS, 5_000L);
		public static final BooleanConfig FAST_FLUSH = new BooleanConfig(null, FASTFLUSH, true);
		public static final IntegerConfig PARKWRITABLE_NANO = new IntegerConfig(null, PARKWRITABLENANO, RateClock.MINOR_WAIT_NS);
		public static final IntegerConfig WAITFLUSH_NS = new IntegerConfig(null, WAITFLUSHNS, 10_000);
		public static final IntegerConfig WRITETIMEOUT_MS = new IntegerConfig(null, WRITETIMEOUTMS, 1);
		public static final IntegerConfig PINGINTERVAL_MS = new IntegerConfig(null, PING_INTERVAL_MS, 3_000L);
		public static final BooleanConfig startWaitStable = new BooleanConfig("START_WAIT_STABLE", "startWaitStable", false);
		public static final IntegerConfig PINGTIMEOUT_MS = new IntegerConfig(null, PING_TIMEOUT_MS, 1_000L);
		public static final IntegerConfig MAX_RETRYCONNECT = new IntegerConfig(null, MAXRETRYCONNECT, 3);
		public static final IntegerConfig CONNECTIONPER_IP = new IntegerConfig(null, CONNECTIONPERIP, 1);
		public static final IntegerConfig DEFAULT_SEND_TIMEOUT_MS = new IntegerConfig(null, SENDTIMEOUTMS, 5000L);
		public static final IntegerConfig DEFAULT_GRACE_TIMEOUT_MS = new IntegerConfig(null, GRACE_TIMEOUTMS, 5000L);
		public static final IntegerConfig MAX_DECODE = new IntegerConfig(null, MAXDECODE, 200);

		public static class Http2Config {
			public static final IntegerConfig HTTP2_INITIAL_CAPACITY = new IntegerConfig(null, INITIALCAPACITY, 1024 * 64);
			public static final IntegerConfig HTTP2_MAX_HEADERLISTSIZE = new IntegerConfig(null, MAXHEADERLISTSIZE, 8192);
			public static final IntegerConfig HTTP2_INITIAL_WINDOWSIZE = new IntegerConfig(null, INITIALWINDOWSIZE, 64 * 1024);
			public static final BooleanConfig ENCODER_ENFORCEMAXCONCURRENTSTREAMS = new BooleanConfig(null, ENCODERENFORCEMAXCONCURRENTSTREAMS, true);
			public static final BooleanConfig IS_UPGRADEHTTP2 = new BooleanConfig(null, ISUPGRADEHTTP2, false);
			public static final BooleanConfig IGNORE_UPGRADEHTTP2 = new BooleanConfig(null, IGNOREUPGRADEHTTP2, false);
			// Properties storedProperties = null;
			public static final IntegerConfig HTTP2_MAXCONTENT_LENGTH = new IntegerConfig(null, MAX_CONTENT_LENGTH, 100 * 1024);
			public static final StringConfig CLIENT_UNREGISTER_PATH = new StringConfig(null, "server.clientUnregisterPath", "/internalClientUnregister");
			public static final StringConfig CLIENT_REGISTER_PATH = new StringConfig(null, "server.clientRegisterPath", "/internalClientRegister");
			public static final StringConfig Http2DebugId = new StringConfig(null, "client.http2.frameLogger.level", "DEBUG");
			public static final IntegerConfig HTTP2_AWAITSETTINGSECONDS = new IntegerConfig(null, HTTP2AWAITSETTINGSECONDS, 5);
			public static final IntegerConfig MAX_CONCURRENTSTREAM = new IntegerConfig(null, MAXCONCURRENTSTREAM, 256);
		}

		public static class CircuitBreakerConfig {
			public static final BooleanConfig ISOLATION_BEHAVIOR_ONISOLATE_CLOSE = new BooleanConfig(null, ISOLATION_BEHAVIOR_ON_ISOLATE_CLOSE, true);
			public static final IntegerConfig ISOLATION_MONITORSPEED = new IntegerConfig(null, ISOLATION_MONITOR_SPEED, 20);
			public static final IntegerConfig ISOLATION_THRESHOLDPERCENT = new IntegerConfig(null, ISOLATION_THRESHOLD_PERCENT, 50);
			public static final BooleanConfig ISOLATE_ENABLE = new BooleanConfig("CHASSIS_ISOLATE_ENABLE", "EnableIsolation", false);
		}

		public static class Http1Config {

			public static final IntegerConfig HTTP1_TIMEOUT_MS = new IntegerConfig(null, HTTP1_DEFAULT_TIMEOUT_MS, 10_000L);
			public static final IntegerConfig HTTP1_SLEEPBETWEENCONNECT_MS = new IntegerConfig(null, HTTP1_SLEEP_BETWEEN_CONNECT_MS, 0L);
			public static final IntegerConfig Http1MAX_CONTENT_LENGTH = new IntegerConfig(null, MAX_CONTENT_LENGTH, 1024 * 1024);
			public static final IntegerConfig HTTP1_QUEUECHANNEL = new IntegerConfig(null, HTTP1_QUEUE_CHANNEL, 200);
		}

		public static class HttpConfig {
			public static final DerivativeConfig<String> USER_AGENT = new DerivativeConfig<>(
				listConfig -> String.format("%s(Java/%s)", listConfig.get(0), listConfig.get(1)),
				new StringConfig(null, AGENT_NAME, "vOCS4.0"),
				new StringConfig(null, "java.version"));

			/**
			 * for server when handle incoming channel -> has ctx
			 * for client when handle response channel -> has ctx
			 * for p2p client and server while handle incoming msg on channel -> has ctx
			 */
			public static final IntegerConfig DEFAULT_HTTP_BUFFER_SIZE = new IntegerConfig(null, "server.requestXbytebuf", 2048);
		}
	}

	public static class MeshConfig {
		public enum PeerRole {client, server}

		public static String readSpecificMeshConfig(PeerRole role, String id, String quotaName, String feature, String defaultVal) {
			return new StringConfig(null, String.format("mesh.%s.%s.%s.%s", role, id, quotaName, feature), defaultVal).get();
		}

		public static String readDefaultMeshConfigs(PeerRole role, Set<String> thisServerProps, String meshConfigId, String feature, String defaultVal) {
			String prop = String.format("mesh.%s.%s.%s", role.name(), meshConfigId, feature);
			return thisServerProps.contains(prop) ? new StringConfig(null, prop, defaultVal).get() : defaultVal;
		}

		public static Set<String> readAllMeshConfigs(String clientId, PeerRole role) {
			return new CollectStringConfig(null, String.format("mesh.%s.%s.", role.name(), clientId)).readPropKeys();
		}

	}

	public static class RouteConfig {
		public static class ChannelMonitorConfig {
			public static final ThreadPoolExecutorConfig CM_CONFIG = new ThreadPoolExecutorConfig("client.channel.monitor", "channel_mon-%d", TimeUnit.MILLISECONDS);
			public static final IntegerConfig MAX_PING_RETRY = new IntegerConfig(null, ChassisConst.CHECK_CLOSE_INTERVAL_MS, 5);
		}

		public static class ConnectionRateLimiterConfig extends RateLimiterConfig {

			public static final IntegerConfig DEFAULT_MAX_CONCURRENT = new IntegerConfig(null, "maxConnectionConcurrent", 0);
			//Netty
			public static final IntegerConfig MAXCONNECTIONRETRY_ACCQUIRE = new IntegerConfig(null, MAXCONNECTIONRETRYACCQUIRE, 10);
		}

		public static class RequestTimeoutConfig {
			public static final StringConfig TIMEOUT_ENGINE = new StringConfig(null, "TimeoutEngine", "ll");
			public static final ThreadPoolExecutorConfig RTM_CONFIG = new ThreadPoolExecutorConfig("client.timeout.handler", "timeout-%d");
			public static final IntegerConfig timeoutScanIntervalMs = new IntegerConfig(null, TIMEOUT_SCAN_INTERVAL_MS, 100);
			public static final IntegerConfig INITIAL_MAP_REQUEST_TIMEOUT_SIZE = new IntegerConfig(null, MAP_TIMEOUT_INITIAL_SIZE, 300);
			public static final BooleanConfig timeoutAsync = new BooleanConfig(null, TIMEOUT_HANDLER_ASYNC, false);
		}

		public static class ServiceDiscoveryConfig {

			public static final IntegerConfig serviceDiscoveryIntervalMs = new IntegerConfig(null, DISCOVERY_INTERVAL_MS, 3_000L);
			public static final BooleanConfig resolveAllUpdateDnsOnInit = new BooleanConfig(null, "client.discovery.initUpdateHost", true);
			public static final IntegerConfig maxRetryResolve = new IntegerConfig(null, MAX_DISCOVERY_RETRY, 10);
//		public static final 	BooleanConfig ENABLE_SERVICEDISCOVERY = new BooleanConfig(null, ENABLESERVICEDISCOVERY, true);
		}

		public static final BooleanConfig SELECT_WITH_ROUTE_KEY = new BooleanConfig(null, "oldMode");
	}


	public static class MetricConfig {
		public static final ThreadPoolExecutorConfig METRIC_PUSH_CONFIG = new ThreadPoolExecutorConfig("mano.metric", "metric-fetch-%d", TimeUnit.MILLISECONDS);
		public static final QuartzConfig METRIC_COLLECTOR_CONFIG = new QuartzConfig("chassis.metric", "METRIC_COLLECTOR", "metric-cache"); //	 public static final 	long METRIC_COLLECT_INTERVAL_MS = TimeUnit.SECONDS.toMillis(new IntegerConfig(null, "metric.collect.interval.sec", 5L).get()); // =>  METRIC_COLLECTOR_CONFIG.INTERVAL_DURATION
		public static final StringConfig SERIES_TYPE_KEY = new StringConfig(null, "chassis.metric.series.type.key", "type");
		public static final StringConfig INSTANCE_LABEL = new StringConfig(null, "chassis.metric.pod.ip.label", "instance");
		public static final StringConfig NODE_NAME_LABEL = new StringConfig(null, "chassis.metric.node.name.label", "node");
		public static final IntegerConfig MAX_CACHE_POINT = new IntegerConfig(null, "chassis.metric.max.point.cache", 30 * 60);//30 mins
		public static final IntegerConfig MAX_FETCH_POINT = new IntegerConfig(null, "chassis.metric.max.point.fetch", 2 * 60);//2 mins
		public static final StringConfig METRIC_PORT = new StringConfig(null, "chassis.metric.port", "9000");
		//		IntegerConfig METRIC_PUSH_INTERVAL_SEC = new IntegerConfig(null, ManoConst.METRIC_PUSH_INTERVAL_SEC, 30);
		public static final IntegerConfig MAX_DATUM_PER_OBJECT = new IntegerConfig(null, ManoConst.MANO_MAX_DATUM_PER_OBJECT, 60);
		public static final IntegerConfig MAX_OBJECT_PER_PUSH = new IntegerConfig(null, ManoConst.MANO_MAX_OBJECT_PER_PUSH, 300);
		public static final IntegerConfig QUEUE_METRIC_SIZE = new IntegerConfig(null, ManoConst.MANO_QUEUE_METRIC_SIZE, 600); //10mins
		public static final IntegerConfig MANO_METRIC_PUSH_RETRY = new IntegerConfig(null, ManoConst.MANO_METRIC_PUSH_RETRY, 0); // 10mins;
		public static final IntegerConfig magicResolveWaitMs = new IntegerConfig(null, "mano.alarm.magic.ms", 20L);
		public static final BooleanConfig METRIC_PUSH_HASH_ENABLE = new BooleanConfig(null, ManoConst.MANO_METRIC_PUSH_HASH_ENABLE, false);
		public static final BooleanConfig DEFAULT_INIT_METRIC = new BooleanConfig(null, "metric.initDefault", false);
		public static final BooleanConfig METRIC_LOG_CACHE = new BooleanConfig(null, "chassis.metric.logCache", true);
		public static final IntegerConfig CACHE_METRIC_PARK_NANO = new IntegerConfig(null, "chassis.metric.park.nano", 10000L);
		public static final IntegerConfig CACHE_METRIC_MAX_ENQUEUE_MS = new IntegerConfig(null, "chassis.metric.max.enqueue.ms", 0L);
		public static final StringConfig METRIC_DATETIME_FORMAT = new StringConfig(null, "chassis.metric.datetime.format", CommonConst.DATE_TIME_FORMAT);
		public static final SimpleDateFormat METRIC_SIMPLE_DATE_FORMAT = new SimpleDateFormat(METRIC_DATETIME_FORMAT.get());
		public static final BooleanConfig METRIC_IGNORE_ZERO_CPU = new BooleanConfig(null, "chassis.cpuMetric.ignoreZero", true);
		public static final StringConfig METRIC_KEY_PATTERN = new StringConfig(null, ManoConst.MANO_METRIC_PUSH_KEY_PATTERN, "yyyyMMdd_HHmmss");
		public static final StringConfig METRIC_PREFIX_THREAD_NAME = new StringConfig(null, "mano.metric.pusher.threadName", "metric-push-");
		public static final IntegerConfig SLEEP_PER_PUSH = new IntegerConfig(null, "mano.metric.pusher.sleep.ms", 1000L);
		public static final IntegerConfig GRACE_TIME = new IntegerConfig(null, "mano.metric.push.graceful.ms", 5000L);
		public static final IntegerConfig clearCacheThresholdSec = new IntegerConfig(null, "chassis.metric.clearCacheThresholdSec", 0L);
		public static final IntegerConfig numPusherThread = new IntegerConfig(null, ManoConst.MANO_METRIC_PUSH_THREAD_COUNT, 2);
//		public static final IntegerConfig initialMetricDelay = new IntegerConfig(null, ManoConst.MANO_METRIC_PUSH_INITIAL_DELAY_SEC, 5);

		public static final IntegerConfig MAX_RETRY_PUSH = new IntegerConfig(null, "mano.metric.pusher.max.retry", 4);
		public static final IntegerConfig metricCpuIntervalSec = new IntegerConfig(null, "chassis.metric.cpuIntervalSec", 0);
	}

	public static class PeerHandlerConfig {
		public static final BooleanConfig DECOMPOSECTX_ON_DONE = new BooleanConfig(null, DECOMPOSE_CTX_ON_DONE, true);
		// peer
		public static final BooleanConfig IS_AFFINITY = new BooleanConfig(null, AFFINITY, false);
		public static final BooleanConfig isHandlerpool = new BooleanConfig(null, HANDLER_POOL, false);
		public static final IntegerConfig CHANNEL_SORCVBUF = new IntegerConfig(null, CHANNELSORCVBUF, 4 << 20);
		public static final IntegerConfig CHANNEL_SOSNDBUF = new IntegerConfig(null, CHANNELSOSNDBUF, 4 << 20);
		public static final IntegerConfig CHANNEL_WRITEBUFFERWATERMARKLOW = new IntegerConfig(null, CHANNELWRITEBUFFERWATERMARKLOW, 10 * 1024 * 1024);
		public static final IntegerConfig CHANNEL_WRITEBUFFERWATERMARKHIGH = new IntegerConfig(null, CHANNELWRITEBUFFERWATERMARKHIGH, 40 * 1024 * 1024);
		public static final BooleanConfig EPOLL_ENABLE = new BooleanConfig(null, EPOLL, true);
		public static final BooleanConfig PREFER_DIRECT = new BooleanConfig(null, PREFERDIRECT, false);
		public static final BooleanConfig POOLED_BYTEBUFFER = new BooleanConfig(null, POOLEDBYTEBUFFER, false);
		public static final IntegerConfig NUM_WORKERTHREAD = new IntegerConfig(null, NUMWORKERTHREAD, JavaConfig.N_CORE.get());
		public static final IntegerConfig NUM_BOSSTHREAD = new IntegerConfig(null, NUMBOSSTHREAD, 1);
		public static final IntegerConfig EVENTTHREAD_COUNT = new IntegerConfig(null, EVENT_THREAD_COUNT, 1);

		//		public static final IntegerConfig MAXCONNECTED_SERVICE = new IntegerConfig(null, MAXCONNECTEDSERVICE, 0);
		public static class ChecksumConfig {

			public static final BooleanConfig DEFAULT_IS_ENABLEMD5 = new BooleanConfig(null, DEFAULTISENABLEMD5);
			public static final BooleanConfig DEFAULT_CLIENT_USE_MD5 = new BooleanConfig(null, CLIENT_IS_ENABLEMD5, DEFAULT_IS_ENABLEMD5);
			public static final BooleanConfig DEFAULT_SERVER_USE_MD5 = new BooleanConfig(null, SERVER_IS_ENABLEMD5, DEFAULT_IS_ENABLEMD5);
		}
	}

	public static class HealthcheckConfig {
		public static final ThreadPoolExecutorConfig H1Healthcheck_CONFIG = new ThreadPoolExecutorConfig("client.http.healthCheck", "h1_check-%d");
		public static final ListIntegerConfig HEALTHY_HTTP_CODE = new ListIntegerConfig(null, HTTP_HEALTHCHECK_SUCCESS_CODES, List.of(200L, 204L));
		public static final BooleanConfig HEALTH_CHECK_BYPASS = new BooleanConfig(null, "mano.healthcheck.bypass", true);
		public static final IntegerConfig HEALTHCHECK_TIMEOUT_SEC = new IntegerConfig(null, "chassis.healthcheck.checkTimeoutSec", 3600);
		public static final IntegerConfig healthcheck_logIntervalSec = new IntegerConfig(null, HTTP_HEALTHCHECK_LOG_SEC, 60);
		public static final IntegerConfig healthcheck_maxHangCount = new IntegerConfig(null, HTTP_HEALTHCHECK_MAX_HANG, 5);
		public static final IntegerConfig HTTP_HEALTHCHECKINTERVAL_SEC = new IntegerConfig(null, HTTP_HEALTHCHECK_INTERVAL_SEC, 30);
		public static final IntegerConfig HTTP_HEALTHCHECKTIMEOUT_MS = new IntegerConfig(null, HTTP_HEALTHCHECK_TIMEOUT_MS, 10_000);
		public static final IntegerConfig HTTP_HEALTHCHECKMAX_RETRY = new IntegerConfig(null, HTTP_HEALTHCHECK_MAX_RETRY, 5);
	}

	public static class ManoConfig {
		public static final PodLocation instanceLocation = PodLocation.of(CommonConfig.InstanceInfo.SITE.get(), CommonConfig.InstanceInfo.VIM.get());
		public static final BooleanConfig MANO_ENABLE = new BooleanConfig("MANO_ENABLE", ManoConst.MANO_ENABLE, true);
		public static final BooleanConfig BYPASS_MANO = HealthcheckConfig.HEALTH_CHECK_BYPASS;
		public static final ThreadPoolExecutorConfig ManoPool_CONFIG = new ThreadPoolExecutorConfig("mano.job", "mano-%d", "MINUTES");
		public static final ThreadPoolExecutorConfig MANO_UDA_CONFIG = new ThreadPoolExecutorConfig("mano.uda", "mano_uda-%d", "SECONDS");
		public static final IntegerConfig MANO_MAX_ENQUEUE_MS = new IntegerConfig(null, ManoConst.MANO_QUEUE_MAX_OFFER_MS, 0L);
		// update
		public static final StringConfig BLUE_GREEN_SUFFIX = new StringConfig(null, MANOUPDATESUFFIX, "-green");
		public static final IntegerConfig manoGraceWait = new IntegerConfig(null, "client.blue.green.grace.ms", 0L);
		// communication
		public static final IntegerConfig MANO_WAIT_RETRY = new IntegerConfig(null, ManoConst.MANO_WAIT_RETRY, 5);
		public static final IntegerConfig MANO_RETRY_COUNT = new IntegerConfig(null, ManoConst.MANO_RETRY_COUNT, 20);
		public static final IntegerConfig MANO_REQUEST_TIMEOUT_SEC = new IntegerConfig(null, ManoConst.MANO_REQUEST_TIMEOUT_SEC, 10);
		public static final BooleanConfig MANO_USE_ASYNC_REQUEST = new BooleanConfig(null, ManoConst.MANO_ASYNC, true);
		public static final ListIntegerConfig MANO_SUCCESS_CODES = new ListIntegerConfig(null, ManoConst.MANO_REQUEST_SUCCESS_CODE, List.of(200L, 204L));

		public static final StringConfig VNFM_ID = new StringConfig(CommonConst.ManoEnvConstant.VNFM_ID.name(), null, "admin");
		public static final StringConfig VNFM_SECRET = new StringConfig(CommonConst.ManoEnvConstant.VNFM_SECRET.name(), null, "admin");

		public static final DerivativeConfig<String> bearerToken = new DerivativeConfig<>(lc -> Base64.getEncoder().encodeToString((lc.get(0) + ":" + lc.get(1)).getBytes(StandardCharsets.UTF_8)), VNFM_ID, VNFM_SECRET);

		//// Endpoints
		public static final StringConfig MANO_AUTHORIZATION_SERVER = new StringConfig(CommonConst.ManoEnvConstant.MANO_AUTHORIZATION_SERVER.name(), null);
		public static final StringConfig VNFC_NOTIFICATION_ENDPOINT = new StringConfig(CommonConst.ManoEnvConstant.VNFC_NOTIFICATION_ENDPOINT.name(), null);
		public static final StringConfig MANO_RECEIVED_ALARM_URI = new StringConfig(CommonConst.ManoEnvConstant.MANO_RECEIVED_ALARM_URI.name(), null);
		public static final StringConfig MANO_METRIC_RECEIVE_URI = new StringConfig(CommonConst.ManoEnvConstant.MANO_METRIC_RECEIVE_URI.name(), null);
		//// API
		public static final StringConfig TRACING_PATH = new StringConfig(null, "mano.path.tracing", "/chassis/tracing");
		public static final StringConfig TRACING_CONFIG = new StringConfig(null, "mano.path.tracingConfig", "/chassis/tracing"); // TODO trung lap
		public static final StringConfig CONNECTION_PATH = new StringConfig(null, "mano.path.connection", "/chassis/connection");
		public static final StringConfig CHASSIS_STAT_CHECK = new StringConfig(null, "mano.path.chassisStatCheck", "/chassis/connection"); // TODO trung lap
		public static final StringConfig ALARM_PATH = new StringConfig(null, "mano.path.alarmPath", "/alarm");
		public static final StringConfig CUSTOM_ALARM = new StringConfig(null, "mano.path.customAlarm", "/mano/alarm");
		public static final StringConfig METRIC_PATH = new StringConfig(null, "mano.path.metric", "/metrics");
		public static final StringConfig SHUTDOWN_PATH = new StringConfig(null, "mano.path.shutdown", "/shutdown");
		public static final StringConfig CLIENT_ROUTING = new StringConfig(null, "mano.path.clientRouting", "/update-step/result");
		public static final StringConfig INTI_CHECK_LIST_PATH = new StringConfig(null, "mano.path.checkList", "/checklist");
		public static final StringConfig BYPASS_HEALTH_CHECK_URL = new StringConfig(null, ManoConst.MANO_PATH_BYPASS_KEY, "/bypass");
		public static final StringConfig AUTHEN = new StringConfig(null, ManoConst.MANO_PATH_BYPASS_KEY, "/authen");
		public static final StringConfig NOTIFY = new StringConfig(null, ManoConst.MANO_PATH_BYPASS_KEY, "/notify");
		public static final StringConfig TOGGLE_ALARM = new StringConfig(null, "mano.path.toggleAlarm", "/toggleAlarm");
		public static final StringConfig TOGGLE_METRIC = new StringConfig(null, "mano.path.toggleMetric", "/toggleMetric");
		public static final StringConfig CLEAR = new StringConfig(null, "mano.path.clear", "/mano/clear");
		public static final StringConfig CHASSIS_THREAD_POOL = new StringConfig(null, "mano.path.chassisThreadPool", "/chassis/pool");
		public static final StringConfig EXPORT_PATH = new StringConfig(null, "mano.path.export", "/export");
		public static final StringConfig MANO_STATUS_PATH = new StringConfig(null, "mano.path.status", "/mano/status");
		public static final StringConfig HEALTH_CHECK_PATH = new StringConfig(null, "mano.path.health", "/healthcheck");
		public static final StringConfig DEFAULT_MANO_HOST = new StringConfig("MANO_HOST", "mano.host");
		public static final IntegerConfig DEFAULT_MANO_PORT = new IntegerConfig("MANO_PORT", "mano.port");


		public static class AlarmConfig {
			public static final IntegerConfig MAP_WAIT_ALARM_SIZE = new IntegerConfig(null, ManoConst.MANO_ALARM_WAIT_SIZE, 200);
			public static final BooleanConfig INIT_ALARM_STATE = new BooleanConfig(null, ManoConst.MANO_ALARM, true);
			public static final StringConfig ALARM_CACHING_KEY_SEPARATOR = new StringConfig(null, ManoConst.MANO_CACHING_KEY_SEPARATOR, "@@");
			public static final IntegerConfig SLEEP_PER_CHECK_MS = new IntegerConfig(null, ManoConst.MANO_ALARM_CACHING_SLEEP_PER_CHECK_MS, 5000L);
			public static final IntegerConfig MAXIMUM_RESOLVE_WAIT_SEC = new IntegerConfig(null, ManoConst.MANO_ALARM_MAXIMUM_RESOLVE_WAIT_SEC, 120L);
			public static final IntegerConfig ALARM_RESPONSE_MAX_WAIT_SEC = new IntegerConfig(null, ManoConst.MANO_ALARM_RESPONSE_MAX_WAIT_SEC, 5L);
			public static final IntegerConfig ALARM_CACHE_MAX_MS = new IntegerConfig(null, "chassis.exception.alarmCacheTimeMs", 30_000L);
		}
	}

	public static class TracingConfig {
		public static final DerivativeConfig<String> DEFAULT_TRACING_PATH = CommonConfig.JavaConfig.makeDeriFilePath("tracing.yml");
		public static final IntegerConfig JAEGER_AGENT_PORT = new IntegerConfig("JAEGER_AGENT_PORT");
		public static final StringConfig JAEGER_ENDPOINT = new StringConfig("JAEGER_ENDPOINT", null, "http://10.0.111.51:9268/api/traces");
		public static final StringConfig POD_TYPE_KEY = new StringConfig(null, "chassis.metric.pod.type.key", "pod_type");
		public static final StringConfig POD_ID_KEY = new StringConfig(null, "chassis.metric.pod.id.key", "pod_id");
		public static final ThreadPoolExecutorConfig TracingPool_CONFIG = new ThreadPoolExecutorConfig("chassis.tracing", "tracing_mon-%d", "MINUTES");
		public static final IntegerConfig MAX_TRACING_IDLE_SEC = new IntegerConfig(null, "chassis.tracing.maxTracingIdleSec", 600L);
		public static final IntegerConfig tracingIdleIntervalSec = new IntegerConfig(null, "chassis.tracing.check.idle.interval.sec", 20);
		public static final IntegerConfig DEFAULT_MAX_TRACING_OBJ = new IntegerConfig(null, "chassis.tracing.max.object", 10);
		public static final IntegerConfig MAX_LOG_ASYNC_WAIT = new IntegerConfig(null, "chassis.tracing.async.log.waitMs", 1000);
	}

	public static class ExceptionClientConfig {

		public static final StringConfig EXCEPTION_PREFIX_THREAD_NAME = new StringConfig(null, "chassis.exception.pusher.threadName", "push-exception-");

		public static class RestConfig {
			public static final DerivativeConfig<String> REST_PROPERTIES_FILE = CommonConfig.JavaConfig.makeDeriFilePath("rest/reset-config.properties");
//			public static final StringConfig REST_PATH_CONFIG = new StringConfig("REST_PATH_CONFIG", "REST_PATH_CONFIG", REST_PROPERTIES_FILE);
//			public static final IntegerConfig REST_MAX_POOL_SIZE = new IntegerConfig(ManoConst.ExceptionClientConst.REST_MAX_POOL_SIZE_KEY, 1);
//			public static final IntegerConfig REST_NUM_CLIENT = new IntegerConfig(ManoConst.ExceptionClientConst.REST_NUM_CLIENT_KEY, 1);
//			public static final BooleanConfig REST_USE_AFINITY = new BooleanConfig(ManoConst.ExceptionClientConst.REST_USE_AFINITY_KEY);
//			public static final StringConfig REST_HOST_RECEIVE = new StringConfig(ManoConst.ExceptionClientConst.REST_HOST_RECEIVE_KEY, ManoConst.ExceptionClientConst.REST_HOST_RECEIVE_KEY, "localhost");
//			public static final IntegerConfig REST_PORT_RECEIVE = new IntegerConfig(ManoConst.ExceptionClientConst.REST_PORT_RECEIVE_KEY, 8080);
//			public static final IntegerConfig REST_SECURE_PORT_RECEIVE = new IntegerConfig(ManoConst.ExceptionClientConst.REST_SECURE_PORT_RECEIVE_KEY, 8443);
			public static final StringConfig REST_ALARM_ENDPOINT = new StringConfig(ManoConst.ExceptionClientConst.REST_ALARM_ENDPOINT_KEY, ManoConst.ExceptionClientConst.REST_ALARM_ENDPOINT_KEY, "/vnffm/v1/alarms/notifications");
//			public static final StringConfig HTTP_PROTOCOL = new StringConfig(ManoConst.ExceptionClientConst.HTTP_PROTOCOL_KEY, ManoConst.ExceptionClientConst.HTTP_PROTOCOL_KEY, ManoConst.TypeParams.HTTP1);
		}

		public static class ClientConfig {
//			public static final DerivativeConfig<String> EXCEPTION_CONFIG_PATH = CommonConfig.JavaConfig.makeDeriFilePath("client/exception-client.properties");
//			public static final StringConfig CLIENT_PATH_CONFIG = new StringConfig("CLIENT_PATH_CONFIG", "CHASSIS_CLIENT_PATH_CONFIG", EXCEPTION_CONFIG_PATH);
			public static final IntegerConfig SENDER_POOL_SIZE = new IntegerConfig(ManoConst.ExceptionClientConst.SENDER_POOL_SIZE_KEY, "CHASSIS_SENDER_POOL_SIZE", 2);
			public static final IntegerConfig CLIENT_EX_QUEUE_CAPACITY = new IntegerConfig(ManoConst.ExceptionClientConst.CLIENT_EX_QUEUE_CAPACITY_KEY, "CHASSIS_CLIENT_EX_QUEUE_CAPACITY", 1000);
			public static final IntegerConfig MAP_CACHE_ID_CAPACITY = new IntegerConfig(ManoConst.ExceptionClientConst.MAP_CACHE_ID_CAPACITY_KEY, "CHASSIS_MAP_CACHE_ID_CAPACITY", 1000);
//			public static final StringConfig APPENDER_TYPE = new StringConfig(ManoConst.ExceptionClientConst.APPENDER_TYPE_KEY, "CHASSIS_APPENDER_TYPE", ManoConst.TypeParams.REST_APPENDER);
		}
	}
}
