package com.viettel.vocs.microchassis.base;

import com.viettel.vocs.common.CommonConst;
import com.viettel.vocs.common.os.CPUMonitor;
import io.netty.handler.codec.http.HttpVersion;

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public interface ChassisConst {

	//region Channel Configuration
	String CHANNELSORCVBUF = "chassis.netty.channel.SO_RCVBUF";
	String CHANNELSOSNDBUF = "chassis.netty.channel.SO_SNDBUF";
	String CHANNELWRITEBUFFERWATERMARKLOW = "chassis.netty.channel.write.buffer.watermark.low";
	String CHANNELWRITEBUFFERWATERMARKHIGH = "chassis.netty.channel.write.buffer.watermark.high";
	String EPOLL = "chassis.netty.channel.epoll";
	String PREFERDIRECT = "chassis.netty.channel.prefer.direct";
	String POOLEDBYTEBUFFER = "chassis.netty.channel.pooled.byte.buffer";
	String NUMWORKERTHREAD = "chassis.netty.channel.num.worker.thread";
	String NUMBOSSTHREAD = "chassis.netty.channel.num.boss.thread";
	String BACKLOG = "chassis.netty.channel.backLog";
	String DECOMPOSE_CTX_ON_DONE = "channel.decomposeCtxOnDone";
	String TIMEOUTCONNECTMS = "chassis.netty.channel.timeout.connect.ms";
	String MAXCHANNELWRITABLEMS = "chassis.netty.channel.max.writable.ms";
	String DEFAULTISENABLEMD5 = "isEnableMd5";
	String MAXCONNECTIONRETRYACCQUIRE = "chassis.netty.channel.max.accquire.retry";
	String PARKWRITABLENANO = "chassis.netty.channel.park.writable.nano";
	String MAXDECODE = "chassis.netty.channel.tcp.max.decode";
	String FASTFLUSH = "chassis.netty.channel.tcp.fast.flush";
	String AFFINITY = "chassis.netty.channel.affinity";
	String HANDLER_POOL = "chassis.netty.channel.handler.pools";

	//endregion
	//region Client Configuration
	String EVENT_THREAD_COUNT = "client.event.thread.count";
//	String MONITOR_INTERVAL_MS = "client.monitor.interval.ms"; => CM_CONFIG INTERVAL_DURATION
	String PING_TIMEOUT_MS = "client.ping.timeout.ms";
	String SENDTIMEOUTMS = "send.timeout.ms";
	String GRACE_TIMEOUTMS = "send.graceful.timeout.ms";
	String WRITETIMEOUTMS = "client.write.timeout.ms";
	String WAITFLUSHNS = "client.write.flush.ns";
	String ENABLESERVICEDISCOVERY = "client.service.discovery.enable";
	String MAXCONNECTEDSERVICE = "client.maxConnected.service";
	String CLIENT_IS_ENABLEMD5 = "client.isEnableMd5";
	String CLIENT_WARMUP_ENABLE = "CLIENT_WARMUP_ENABLE";
	String WARMUP_TPS_STEPS = "client.warmup.tps.steps";
	String MAXRETRYCONNECT = "client.max.retry.connect";
	String CONNECTIONPERIP = "client.connection.per.ip";
	String PING_INTERVAL_MS = "client.ping.interval.ms";
	//endregion
	//region Connection Configuration
	String MANOUPDATESUFFIX = "client.mano.upgrade.suffix";

	String CHANNELCHECKTIMEOUTSEC = "client.channel.check.timeout.sec";
	//endregion
	//region Http2 Configuration
	String INITIALCAPACITY = "client.http2.initial.capacity";
	String HTTP2AWAITSETTINGSECONDS = "client.http2.wait.setting.seconds";
	String MAXCONCURRENTSTREAM = "client.http2.max.concurrent.stream";
	String MAXHEADERLISTSIZE = "client.http2.max.header.list.size";
	String INITIALWINDOWSIZE = "client.http2.initial.window.size";
	String ENCODERENFORCEMAXCONCURRENTSTREAMS = "client.http2.encoder.enforce.max.concurrent.streams";
	String ISUPGRADEHTTP2 = "client.http2.is.upgrade.http2";
	String PINGTIMEOUTMS = "client.http2.ping.timeout.ms";
	String IGNOREUPGRADEHTTP2 = "client.http2.ignore.upgrade.http2";
	//endregion
	//region Server Configuration
	String SERVER_IS_ENABLEMD5 = "server.md5.enable";
	//endregion
	//region Channel Monitor
	String CHECK_CLOSE_INTERVAL_MS = "client.monitor.checkCloseIntervalMs";
	//endregion
	//region Connection Manager
	String DEFAULT_CHANNEL_CHECK_TIMEOUT_SEC = "client.channelCheckTimeoutSec";
	String LOG_BUFFER_INTERVAL = "chassis.log.buffer.interval.sec";
	String LOG_QUEUE_INTERVAL = "chassis.log.queue.interval.sec";
	//endregion
	//region Request Timeout Monitor
	String TIMEOUT_SCAN_INTERVAL_MS = "client.timeout.scan.interval.ms";
	String MAP_TIMEOUT_INITIAL_SIZE = "client.timeout.initial.map.size";
	String TIMEOUT_HANDLER_ASYNC = "client.timeout.handler.async";
	//endregion
	//region Service Monitor
	String UPDATE_WEIGHT_INTERVAL = "client.discovery.update.weight.interval.ms";
	String MAX_DISCOVERY_RETRY = "client.discovery.max.retry";
	String DISCOVERY_INTERVAL_MS = "client.discovery.interval.ms";
	//endregion
	//region Http1Connection
	String HTTP1_QUEUE_CHANNEL = "client.http1.connection.pool.size";
	String HTTP1_MAX_ENQUEUE_CHANNEL_MS = "client.http1.connection.max.enqueue.ms";
	String HTTP1_MAX_CC = "client.http1.max.inflight.channel";
	String HTTP1_SLEEP_BETWEEN_CONNECT_MS = "client.http1.connect.sleep.ms";
	String HTTP1_PARK_NANO = "client.http1.park.nano";
	String HTTP1_DEFAULT_TIMEOUT_MS = "client.http1.timeout.ms";
	String HTTP1_MAX_CLOSE_WAIT_MS = "client.http1.max.close.wait.ms";
	String HTTP1_CLOSE_PARK = "client.http1.close.park.nano";


	//endregion
	//region HttpConnection
	String AGENT_NAME = "client.http.agent.namess";
	//endregion
	//region HttpClientOptions
	String MAX_CONTENT_LENGTH = "client.http.max.content.length";
	//endregion
	//region Http2Handler
	String HTTP2_SERVER_QUEUE_SIZE = "server.http2.queue.size";

	//endregion

	//region TimeoutIsolation
	String ISOLATION_INTERVAL_MS = "client.isolation.interval.ms";
	String ISOLATION_MIN_REQUEST_COUNT = "client.min.request.count";
	String ISOLATION_BEHAVIOR_ON_ISOLATE_CLOSE = "client.isolation.behavior.close";
	String ISOLATION_MONITOR_SPEED = "client.isolation.speed.min";
	String ISOLATION_THRESHOLD_PERCENT = "client.isolation.thresholdPct";
	//endregion

	//region HTTP1 healthcheck
	String HTTP_HEALTHCHECK_INTERVAL_SEC = "client.http.healthcheck.interval.sec";
	String HTTP_HEALTHCHECK_TIMEOUT_MS = "client.http.healthcheck.timeout.ms";
	String HTTP_HEALTHCHECK_MAX_RETRY = "client.http.healthcheck.max.retry";
	String HTTP_HEALTHCHECK_LOG_SEC = "client.http.healthCheck.log.interval.sec";
	String HTTP_HEALTHCHECK_MAX_HANG = "client.http.healthCheck.max.hang";
	String HTTP_HEALTHCHECK_SUCCESS_CODES = "client.http.healthCheck.success.codes";
	String HTTP_DEFAULT_INSTANCE_TIMEOUT_MS = "client.http.default.timeout.ms";

	String STANDALONE_LBR_ID = "standaloneLbr";
	int WARMUP_PORT = 9000;
	String WARMUP_HOST = "localhost";
	String pidStatCommand = "pidstat -t -p " + CommonConst.SystemConst.PID + " 2";
	String topCommand = "ps -p " + CommonConst.SystemConst.PID + " -o %cpu,%mem,rss,cputime";
	String defaultHttpClientId = "defaultHttpClient";
	String to3rdPartyMsgId = "to3rdParty";
	String IPV4_REGEX = "(([0-1]?\\d{1,2}\\.)|(2[0-4]\\d\\.)|(25[0-5]\\.)){3}(([0-1]?\\d{1,2})|(2[0-4]\\d)|(25[0-5]))";
	Pattern IPV4_PATTERN = Pattern.compile(IPV4_REGEX);
	CPUMonitor cpuMon = new CPUMonitor(CPUMonitor.CpuCollector.procStat, 20); // must have this to report CPU

	enum CustomHeader {mess_id, pod_type, pod_id;
		public static final String decodeTimeHeader = "chassis-decode-time";
		public static final String inSqHeader = "in-server-queue";
		public static final String inRqHeader = "in-response-queue";
		public static final String msgIdHeader = mess_id.name();
	}

	/**
	 * @author tiennn18
	 */
	interface SupportVersion {
		String HTTP = "HTTP";
		String TCP = "TCP";
		String TCP_SHORT = "t";
		String HTTP_SHORT = "h";
//		String HTTP1_SHORT = HTTP_SHORT + "1";
//		String HTTP2_SHORT = HTTP_SHORT + "2";

		String LBR = "TCP/LBR";

		HttpVersion HTTP_2_0 = new HttpVersion(HTTP, 2, 0, true);
		HttpVersion HTTP_1_1 = HttpVersion.HTTP_1_1;
	//	HttpVersion HTTP_1_0 = HttpVersion.HTTP_1_0;
		String HTTP2_0 = HTTP_2_0.text();
		String HTTP1_1 = HttpVersion.HTTP_1_1.text();
		String HTTP1_0 = HttpVersion.HTTP_1_0.text();
	}

	interface LBRConst{
		String ASSIGN_FILL = "fill";
		String ASSIGN_HASH = "hash";
	}

	/**
	 * @author tiennn18
	 */
	interface ManoConst {
		String manoHttpClientId = "manoClient";
		AtomicReference<String> manoHttpServerId = new AtomicReference<>("manoServer");
		String MANO_WAIT_RETRY = "mano.retry.wait";
		String MANO_RETRY_COUNT = "mano.retry.count";
		String MANO_REQUEST_TIMEOUT_SEC = "mano.retry.timeout.sec";
		String MANO_ASYNC = "mano.async";
//		String METRIC_PUSH_INTERVAL_SEC = "metric.push.interval.sec"; // => metricPushThreadConfig.INTERVAL_DURATION
		String MANO_REQUEST_SUCCESS_CODE = "mano.request.success.code";
		String MANO_ALARM_WAIT_SIZE = "mano.alarm.wait.size";
		String MANO_ENABLE = "mano.enable";
		String MANO_ALARM = "mano.alarm";
		String MANO_QUEUE_MAX_OFFER_MS = "mano.queue.max.offer.ms";
		String MANO_MAX_DATUM_PER_OBJECT = "mano.max.datum.per.object";
		String MANO_MAX_OBJECT_PER_PUSH = "mano.max.object.per.push";
		String MANO_PUSH_MERIC_TIMEOUT_SEC = "mano.push.metric.timeout.sec";
		String MANO_QUEUE_METRIC_SIZE = "mano.metric.queue.size";
		String MANO_METRIC_PUSH_RETRY = "mano.metric.push.retry";
		String MANO_METRIC_PUSH_THREAD_COUNT = "mano.metric.push.thread.count";
		String MANO_METRIC_PUSH_INITIAL_DELAY_SEC = "mano.metric.push.initial.delay.sec";
		String MANO_METRIC_PUSH_HASH_ENABLE = "mano.metric.push.hash.enable";
		String MANO_METRIC_PUSH_HASH_QUERY_STRING = "mano.metric.push.hash.query.string";
		String MANO_METRIC_PUSH_HASH_SUCCESS_STRING = "mano.metric.push.hash.success.string";
		String MANO_METRIC_PUSH_HASH_SUCCESS_KEY = "mano.metric.push.hash.success.key";
		String MANO_METRIC_PUSH_RETRY_HTTP_CODES = "mano.metric.push.retry.http.codes";
//		String MANO_METRIC_SCHEDULER_THREAD_COUNT = "mano.metric.scheduler.thread.count";
		String MANO_ALARM_RESPONSE_MAX_WAIT_SEC = "mano.alarm.wait.before.resolve.sec";
		String MANO_METRIC_PUSH_KEY_PATTERN = "mano.metric.push.key.pattern";
		String MANO_CACHING_KEY_SEPARATOR = "mano.alarm.caching.key.separator";
		String MANO_ALARM_CACHING_SLEEP_PER_CHECK_MS = "mano.alarm.caching.sleep.per.check.ms";
		String MANO_ALARM_MAXIMUM_RESOLVE_WAIT_SEC = "mano.alarm.maximum.resolve.wait.check.sec";
		String MANO_PATH_BYPASS_KEY = "mano.path.bypass";
		String getAPIPath = "/getReloadApi";
		String reloadAPIPath = "/reloadPolicy";

		interface ExceptionClientConst{
			String SENDER_POOL_SIZE_KEY = "SENDER_POOL_SIZE";
			String CLIENT_EX_QUEUE_CAPACITY_KEY = "CLIENT_EX_QUEUE_CAPACITY";
			String MAP_CACHE_ID_CAPACITY_KEY = "MAP_CACHE_ID_CAPACITY";
			String APPENDER_TYPE_KEY = "APPENDER_TYPE";
			String REST_MAX_POOL_SIZE_KEY = "REST_MAX_POOL_SIZE";
			String REST_NUM_CLIENT_KEY = "REST_NUM_CLIENT";
			String REST_USE_AFINITY_KEY = "REST_USE_AFINITY";
			String REST_HOST_RECEIVE_KEY = "REST_HOST_RECEIVE";
			String REST_PORT_RECEIVE_KEY = "REST_PORT_RECEIVE";
			String REST_SECURE_PORT_RECEIVE_KEY = "REST_SECURE_PORT_RECEIVE";
			String REST_ALARM_ENDPOINT_KEY = "REST_ALARM_ENDPOINT";
			String HTTP_PROTOCOL_KEY = "HTTP_PROTOCOL";
		}

		interface TypeParams {
				String REST_APPENDER = "REST_APPENDER";
				String HTTP1 = "http1";
				String HTTP2 = "http2";
		}

		interface APIPath {
				String getConfigTypePath = "/config-type";
				String getConfigPath = "/config-param";
		}
	}
	enum Name{
		ISOLATION_CHANNEL_OVER_TIMEOUT_REQUEST,
		INTERNAL_SERVICE_NAME_VERIFICATION_FAIL,
		INTERNAL_CONNECTION_SERVICE_DOWN,
		INTERNAL_CONNECTION_CREATED_FAIL,
		INTERNAL_CONNECTION_DISCONNECT,
		INTERNAL_SERVICE_DISCOVERY_FAIL,
		APPLICATION_EXCEPTION,
		INTERNAL_CONNECTION_EXCEPTION
		;
	}
}
