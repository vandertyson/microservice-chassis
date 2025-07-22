package com.viettel.vocs.microchassis.base;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.datatype.FuseBoolean;
import com.viettel.vocs.common.os.thread.NameableThreadFactory;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.microchassis.metrics.MetricCollector;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.util.concurrent.ThreadFactory;

/**
 * @author tiennn18
 */
public class ChassisThreadManager extends ThreadManager {

	public final static PoolExecutor tracingPool = ChassisConfig.TracingConfig.TracingPool_CONFIG.makeExecutor();

	static { // static for set config
		ChassisConfig.RouteConfig.ChannelMonitorConfig.CM_CONFIG.INTERVAL_DURATION.setDefault(5_000L);
		ChassisConfig.PerformanceConfig.H_CLIENT_HANDLEPOOL_CONFIG.CORE_POOL_SIZE.setDefault(Long.valueOf(CommonConfig.JavaConfig.TOTAL_THREADS.get()));
		ChassisConfig.PerformanceConfig.H_SERVER_HANDLEPOOL_CONFIG.CORE_POOL_SIZE.setDefault(Long.valueOf(CommonConfig.JavaConfig.TOTAL_THREADS.get()));
		ChassisConfig.PerformanceConfig.T_CLIENT_HANDLEPOOL_CONFIG.CORE_POOL_SIZE.setDefault(Long.valueOf(CommonConfig.JavaConfig.TOTAL_THREADS.get()));
		ChassisConfig.PerformanceConfig.T_SERVER_HANDLEPOOL_CONFIG.CORE_POOL_SIZE.setDefault(Long.valueOf(CommonConfig.JavaConfig.TOTAL_THREADS.get()));
	}

	public static final FuseBoolean enableMetricCollector = new FuseBoolean(true);
	public static final Thread threadCPU = ChassisConfig.MetricConfig.metricCpuIntervalSec.getInt() > 0 ? new Thread(MetricCollector.collectorThread) : null;

	public static final ThreadManager.ScheduledExecutor channelMonitorPool = ChassisConfig.RouteConfig.ChannelMonitorConfig.CM_CONFIG.makeScheduledPool();
	public static final ThreadFactory RTM_Factory = new NameableThreadFactory("R_timeout_mon");
	public static final ThreadManager.PoolExecutor timeoutExecutor = ChassisConfig.RouteConfig.RequestTimeoutConfig.RTM_CONFIG.makeExecutor();
	public static final ThreadManager.PoolExecutor createConnPool = ThreadManager.newCachedThreadPool(CommonConfig.ThreadConfig.SharedPool_CONFIG.makeThreadFactory());
	public static final ThreadManager.PoolExecutor hcHandlerPool = ChassisConfig.PerformanceConfig.H_CLIENT_HANDLEPOOL_CONFIG.makeFixed();
	public static final ThreadManager.PoolExecutor hsHandlerPool = ChassisConfig.PerformanceConfig.H_SERVER_HANDLEPOOL_CONFIG.makeFixed();
	public static final ThreadManager.PoolExecutor tcHandlePool = ChassisConfig.PerformanceConfig.T_CLIENT_HANDLEPOOL_CONFIG.makeFixed();
	public static final ThreadManager.PoolExecutor tsHandlePool = ChassisConfig.PerformanceConfig.T_SERVER_HANDLEPOOL_CONFIG.makeFixed();
	static { // static for ignite threads
		if (threadCPU != null && enableMetricCollector.get()) {
			threadCPU.setName("cpu_monitor");
			threadCPU.start();
		}
	}

	public static PoolExecutor makeEventPool(String peerId){
		return newCachedThreadPool(new BasicThreadFactory.Builder()
			.namingPattern(String.format("event-%s", peerId))
			.daemon(true)
			.priority(Thread.MAX_PRIORITY)
			.build());
	}
}
