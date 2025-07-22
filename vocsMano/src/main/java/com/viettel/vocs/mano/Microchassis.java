package com.viettel.vocs.mano;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.mano.service.Mano1Handler;
import com.viettel.vocs.mano.service.ManoIntegration;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.handler.tcp.ServerHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.config.ConnectionConfiguration;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.tracing.client.TracingMonitor;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author tiennn18
 */
public class Microchassis extends com.viettel.vocs.microchassis.Microchassis {

	Consumer<Throwable> onTracingFail;

	public Microchassis setOnTracingFail(Consumer<Throwable> onTracingFail) {
		this.onTracingFail = onTracingFail;
		return this;
	}

	public Microchassis start(Consumer<ConnectionConfiguration> ymlAlter, boolean enableTracing) throws Exception { // khong can throw, vi neu khong mo port server thi khong sao van cho song de debug
		try {
			ConnectionManager.getInstance().loadYmlConfig(ymlAlter);
			if (enableTracing) {
				// call tracing after set config chassis
				logger.info("==================start tracing monitor==================");
				try {
					TracingMonitor.getInstance()
//				.withConfigPath(AbmConstants.DEFAULT_TRACING_YML_PATH)
						.withServiceName(CommonConfig.InstanceInfo.VNF_INSTANCE_NAME.get())
						.startTracing();
				} catch (Exception ex) {
					if(onTracingFail!=null) onTracingFail.accept(ex);
				}
			}
		} catch (Exception e) {
			logger.error(e, e);
			throw e;
		}
		ConnectionManager.getInstance().startMonitorThread();
		return this;
	}

	public Microchassis openServiceMano(Function<HttpServerOptions, Mano1Handler> manoHandlerMaker, Consumer<NettyServer> manoShutdownHook) throws Exception {
		openService(ChassisConst.ManoConst.manoHttpServerId.get(), false, config -> Objects.requireNonNull(manoHandlerMaker).apply((HttpServerOptions) config), manoShutdownHook, server -> ManoIntegration.onInstance(i -> ManoIntegration.notification()));
		return this;
	}

	@Override
	public Microchassis openService(String serverId, boolean isRequired, Function<ServerConfiguration, ServerHandler> serverHandlerMaker, Consumer<NettyServer> shutdownHook, Consumer<NettyServer> afterStartAction) throws Exception {
		super.openService(serverId, isRequired, serverHandlerMaker, shutdownHook, afterStartAction);
		return this;
	}
	public Microchassis setManoServerId(String newYmlServerId) {
		ChassisConst.ManoConst.manoHttpServerId.set(Objects.requireNonNull(newYmlServerId));
		return this;
	}
	@Override
	public Microchassis stopAllServer(Runnable afterCloseServers){
		super.stopAllServer(afterCloseServers);
		return this;
	}

}

