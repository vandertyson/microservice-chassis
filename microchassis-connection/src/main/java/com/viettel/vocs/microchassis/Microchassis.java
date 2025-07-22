package com.viettel.vocs.microchassis;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.handler.tcp.ServerHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import com.viettel.vocs.microchassis.metrics.Metric;
import com.viettel.vocs.microchassis.metrics.MetricCollector;
import com.viettel.vocs.microchassis.util.MonitorManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author tiennn18
 */
public class Microchassis {
	protected static final Logger logger = LogManager.getLogger(Microchassis.class);
	protected static final MonitorManager mon = MonitorManager.getInstance();
	public Metric metric(){
		return MetricCollector.getMetric();
	}
	public Microchassis stopAllServer(Runnable afterCloseServers) {
		ConnectionManager.mapServer.values().parallelStream().filter(s -> s != null && !s.getId().equals(ChassisConst.ManoConst.manoHttpServerId.get())).forEach(NettyServer::stop);
		if(afterCloseServers!=null) afterCloseServers.run();
		return this;
	}
	public Microchassis openService(String serverId, boolean isRequired, Function<ServerConfiguration, ServerHandler> serverHandlerMaker, Consumer<NettyServer> shutdownHook, Consumer<NettyServer> afterStartAction) throws Exception {//end
		NettyServer serviceServer = ConnectionManager.getInstance().getServer(serverId);
		logger.info("Starting server with {} worker thread at port {}", ChassisConfig.PerformanceConfig.BUSINESS_HANDLE_THREAD.get(), serviceServer.getConfig().port);
		serviceServer.start(Objects.requireNonNull(serverHandlerMaker).apply(serviceServer.getConfig()));
		if (isRequired) waitServiceOpen(serviceServer);
		if (shutdownHook != null) {
			logger.info("add graceful shutdown for service " + serverId);
			Runtime.getRuntime().addShutdownHook(new Thread(()-> shutdownHook.accept(serviceServer)));
		}
		if(afterStartAction!=null) afterStartAction.accept(serviceServer);
		return this;
	}
	public HttpServer getManoServer(){
		return ConnectionManager.getInstance().getServer(ChassisConst.ManoConst.manoHttpServerId.get());
	}
	private Microchassis waitServiceOpen(NettyServer serviceServer) throws TimeoutException {
		// Wait for server to start
		long start = TimeUtils.nowNano();
		while (serviceServer.ch != null && !serviceServer.ch.isActive()) {
			if (TimeUtils.isTimeoutSec(start, 10)) {
				String errMes = "Cannot start server port " + serviceServer.getConfig().port + " after 10s";
				logger.error(errMes);
				throw new TimeoutException(errMes);
			}
			TimeUtils.waitSafeSec(1);
			logger.info("Waiting for server port " + serviceServer.getConfig().port + " to start...");
		}
		return this;
	}
}
