package com.viettel.vocs.microchassis.connection.loadbalancing.configure;

import com.viettel.vocs.microchassis.base.ChassisConfig;


public class RCP extends RPL { // must extend RPL to use maxWaitCcNs
	/**
	 * round robin cached pooled
	 * set maxConnectionPerIp > connectionPerIp to enable cached pool feature,
	 * connection wont be reclaimed by revokeOverCreateConns
	 * => connectionPerIp is min created and maxConnectionPerIp is max created
	 */
	public long sleepBetweenConnectMs = Math.max(ChassisConfig.ConnectionConfig.Http1Config.HTTP1_SLEEPBETWEENCONNECT_MS.get(), 1/*default 1 ms*/);

	//	long maxDrainWait = CommonUtils.getLong(HTTP1_MAX_CLOSE_WAIT_MS, 5000L);
	{
		maxConnectionPerIp = ChassisConfig.ConnectionConfig.Http1Config.HTTP1_QUEUECHANNEL.getInt();
	}

	//	public static final long DEFAULT_TIMEOUT = CommonUtils.getLong(HTTP1_DEFAULT_TIMEOUT_MS, 10000L); // use client config timeout
//	protected final Queue<Channel> queueChannel = new MpmcArrayQueue<>(CommonUtils.getInteger(HTTP1_QUEUE_CHANNEL, 20));
//	protected static final long MAX_ENQUEUE_CONNECTION_MS = CommonUtils.getLong(HTTP1_MAX_ENQUEUE_CHANNEL_MS, 1000L);
//	protected final Map<String, Http1ClientResponseCallback> mapResponseCallback = new ConcurrentHashMap<>();
	@Override
	public boolean diff(StrategyConfigure obj) {
		if (super.diff(obj)) return true;
		if (!(obj instanceof RCP)) return true;
		RCP o = (RCP) obj;
		return maxConnectionPerIp != o.maxConnectionPerIp
			|| sleepBetweenConnectMs != o.sleepBetweenConnectMs
			;
	}
}