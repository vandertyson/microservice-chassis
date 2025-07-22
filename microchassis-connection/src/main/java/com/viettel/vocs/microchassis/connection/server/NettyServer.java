/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection.server;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.ocs.ApplicationID;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.base.ChassisThreadManager;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.context.CtxHolder;
import com.viettel.vocs.microchassis.codec.context.ReplyContext;
import com.viettel.vocs.microchassis.codec.handler.tcp.ServerHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.client.ChannelAttribute;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.connection.config.mesh.MeshConfig;
import com.viettel.vocs.microchassis.connection.config.mesh.ServerMeshConfig;
import com.viettel.vocs.microchassis.connection.event.ContextEvent;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.event.InternalEventHandler;
import com.viettel.vocs.microchassis.connection.event.ServerEvent;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.CenterLBR;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.LBRAdapter;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.StandaloneLBR;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter.ServerLimiter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.ServerCounter;
import com.viettel.vocs.microchassis.metrics.Metric;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRServerInfo;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinityThreadFactory;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @param <Conf>   class của config. Ví dụ : HttpServerOptions
 * @param <Initer> class của ChannelInitializer. Ví dụ: ServerInitializer
 * @author vttek
 */
public abstract class NettyServer<Conf extends ServerConfiguration, SHandler extends ServerHandler, Initer extends ServerChannelInitializer<Conf, SHandler>> implements ServerAPI {
	public static final Metric serverMetric = new Metric(Objects.requireNonNull(ApplicationID.get()));
	private static final Logger logger = LogManager.getLogger(NettyServer.class);
	public final AtomicBoolean pause = new AtomicBoolean();
	private final LBRAdapter negotiator;
	public Channel ch;
	@Getter
	protected final Conf config;
	@Getter
	private final ServerCounter counter;
//	protected final Metric serverMetric = ConnectionManager.getServerMetric(); // currently use unified metric in connection manager


	@Getter
	private final ServerLimiter limiter;

	@Getter
	private final Endpoint endpoint;

	protected EventLoopGroup bossGroup;
	protected EventLoopGroup workerGroup;
	protected ServerBootstrap bootstrap;
	@Getter
	protected final ByteBufAllocator bytebufAllocator;
	protected final ChannelGroup connectedChannels = new DefaultChannelGroup("Accepted", GlobalEventExecutor.INSTANCE);

	public long countConnected() {
		return connectedChannels.stream().filter(Routable::isConnected).count();
	}

	protected final ChannelGroup deniedChannels = new DefaultChannelGroup("RejectedButHold", GlobalEventExecutor.INSTANCE);
	@Getter
	protected final EventHandler eventHandler;
	@Getter
	protected final ServerMeshConfig<Conf> mesh;
	protected final LBRAdapter defaultStlnLBRA = new StandaloneLBR(null);
	final BiFunction<Conf, String, ThreadFactory> factoryMaker = (config, id) -> config.channelConfiguration.affinity && !CommonConfig.JavaConfig.tasksetCpus.check().isEmpty() // must enable and set cpus
		? new AffinityThreadFactory("as" + config.getProtocolShortName() + id + "_" + config.id, AffinityStrategies.DIFFERENT_CORE)
		: new BasicThreadFactory.Builder()
		.namingPattern("ds" + config.getProtocolShortName() + id + "_" + config.id + "-%d")
		.daemon(true)
		.priority(Thread.MAX_PRIORITY)
		.wrappedFactory(Executors.defaultThreadFactory())
		.build();

	private ServerBootstrap remakeBoostrap() {
		ServerBootstrap serverBootstrap = new ServerBootstrap();
		boolean epoll = config.channelConfiguration.epoll && Epoll.isAvailable();
		if (!epoll) logger.info("Epoll is not supported. Use NioEventLoopGroup");
		if(bossGroup != null) bossGroup.shutdownGracefully();
		bossGroup = epoll
			? new EpollEventLoopGroup(config.channelConfiguration.numBossThread, factoryMaker.apply(config, "be"))
			: new NioEventLoopGroup(config.channelConfiguration.numBossThread, factoryMaker.apply(config, "bn"));
		if(workerGroup != null) workerGroup.shutdownGracefully();
		workerGroup = epoll
			? new EpollEventLoopGroup(config.channelConfiguration.numWorkerThread, factoryMaker.apply(config, "we"))
			: new NioEventLoopGroup(config.channelConfiguration.numWorkerThread, factoryMaker.apply(config, "wn"));
		serverBootstrap.group(bossGroup, workerGroup)
			.channel(epoll ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
			.option(ChannelOption.TCP_NODELAY, true)
			.option(ChannelOption.SO_REUSEADDR, true)
			.option(ChannelOption.SO_SNDBUF, config.channelConfiguration.channelSoSndBuf)
			.option(ChannelOption.SO_RCVBUF, config.channelConfiguration.channelSoRcvBuf)
			.option(ChannelOption.SO_KEEPALIVE, true)
			.option(ChannelOption.AUTO_READ, true)
			.option(ChannelOption.ALLOCATOR, bytebufAllocator)
			.childOption(ChannelOption.TCP_NODELAY, true)
			.childOption(ChannelOption.SO_SNDBUF, config.channelConfiguration.channelSoSndBuf)
			.childOption(ChannelOption.SO_KEEPALIVE, true)
			.childOption(ChannelOption.SO_RCVBUF, config.channelConfiguration.channelSoRcvBuf)
			.childOption(ChannelOption.SO_REUSEADDR, true)
			.childOption(ChannelOption.AUTO_READ, true)
			.childOption(ChannelOption.ALLOCATOR, bytebufAllocator);
		if (config.channelConfiguration.backLog > 0) {
			serverBootstrap.option(ChannelOption.SO_BACKLOG, config.channelConfiguration.backLog);
			serverBootstrap.childOption(ChannelOption.SO_BACKLOG, config.channelConfiguration.backLog);
		}
		return serverBootstrap;
	}
	@Getter private final MsgCounter msgCounter;
	protected NettyServer(Conf originConfig) {
		try{
			counter = new ServerCounter(config = Objects.requireNonNull(originConfig, "Server config is required"));
	 		msgCounter = MsgCounter.ofServer(config.id);
			ConnectionManager.mapServer.putIfAbsent(this.config.id, this);
			eventHandler = new InternalEventHandler(config.id, ChassisThreadManager.makeEventPool(String.format("s-%s", config.id)));
			limiter = new ServerLimiter(config.rateLimiter, counter); // include no limit if maxTPS and maxCC = 0
			endpoint = Endpoint.newEndpoint(ConnectionManager.getMyDNS(), config.port);
			Objects.requireNonNull(config.channelConfiguration, "Server channel config is required");
			String configString = ReflectionToStringBuilder.toString(config, ToStringStyle.MULTI_LINE_STYLE, false);
			logger.info(configString);
			mesh = new ServerMeshConfig<>(config);
			negotiator = getNegtor();
			logger.info("Server {} use negtor {}", config.id, negotiator.getClass().getSimpleName());
			negotiator.registerServer(this);
			logger.info("Use " + config.channelConfiguration.numWorkerThread + " worker thread for server");
			bytebufAllocator = config.channelConfiguration.pooledByteBuffer
				? new PooledByteBufAllocator(config.channelConfiguration.preferDirect)
				: new UnpooledByteBufAllocator(config.channelConfiguration.preferDirect);
			eventHandler.addEventHandler(ContextEvent.EventType.CHANNEL_ACTIVE, (Consumer<ContextEvent>) event -> {
				/** accept all incoming connections, only drop if conn speaks for the first time in msgDecoder
				 * if connection not obey the ask protocol of mesh, then allow to avoid integration dumps :))
				 */
				Channel channel = event.getChannel();
				if (negotiator instanceof CenterLBR) {
					CenterLBR centerLBR = (CenterLBR) negotiator;
					LBRServerInfo lbrServer = centerLBR.getServer(getId());
					mesh.read(lbrServer); // update before iterate create connection
				}
				if (MeshConfig.Mode.FULL_MESH.equals(mesh.mode) || checkAllowNewConnection(event.getEndpoint()))
					connectedChannels.add(channel); // always add, but not register
				else deniedChannels.add(channel);
				negotiator.updateServer(this);
				logger.info("[New client connected]{type={}, ctx={}, pre_add={} max={}}", config.getProtocolName(), channel, countConnected(), mesh.maxConnection);

			}).addEventHandler(ContextEvent.EventType.CHANNEL_INACTIVE, (Consumer<ContextEvent>) event -> {
				if (negotiator instanceof CenterLBR)
					mesh.read(((CenterLBR) negotiator).getServer(getId())); // update before iterate create connection
				Channel channel = event.getChannel();
				if (!connectedChannels.remove(channel))
					deniedChannels.remove(channel); // auto remove on channel close
				negotiator.updateServer(this);
				logger.info("[Client disconnected]{type={}, ctx={}, connected={}}", config.getProtocolName(), channel, countConnected());
				event.getCtx().close();
			});
		} catch (Throwable ex) {
			logger.error(ex, ex);
			throw ex;
		}
	}

	public abstract LBRAdapter getNegtor();

	private final AtomicInteger eventloopQueueSize = new AtomicInteger(0);


	public int workerTaskQueueSize() {
		eventloopQueueSize.set(0);
		workerGroup.forEach(e -> eventloopQueueSize.addAndGet(((SingleThreadEventLoop) e).pendingTasks()));
		return eventloopQueueSize.get();
	}

	public long stateIfAllowedToConnect(Channel channel) { // newEndpoint ask before add to connectedChannels, so need to compare with +1
		if (negotiator instanceof CenterLBR) {
			Endpoint newConnectionEndpoint = ChannelAttribute.getEndpoint(channel);
			CenterLBR centerLBR = (CenterLBR) negotiator;
			LBRServerInfo lbrServer = centerLBR.getServer(getId());
			Long maxAllowOfDest = lbrServer.targetMap.get(newConnectionEndpoint.ip);
			logger.info("Check allow center for ip {} with max allow {} from map {}", newConnectionEndpoint.ip, maxAllowOfDest, lbrServer.status());
			Long countConnected;
			if (!(maxAllowOfDest != null
				&& (countConnected = countIps().get(newConnectionEndpoint.ip)) != null
				&& countConnected <= maxAllowOfDest // co dau = vi channel da add vao channelGroup roi
//				&& checkAllowNewConnection(newConnectionEndpoint) // da check o CHANNEL_ACTIVE
			)) return -1;
		}
		if (connectedChannels.contains(channel)) return countConnected();
		else return -1; // if full host -1 or denied
	}

	public boolean checkAllowNewConnection(Endpoint newEndpoint) { // newEndpoint ask before add to connectedChannels, so need to compare with +1
		// for ask connection, askConn count as 1 -> full connection -> return -1 to ask request ~ impolite/maxClient server
		// so need isAskConnection, to return with equal
		Map<String, Long> ipConns = countIps();
		boolean isAccept =
			countConnected() + (newEndpoint != null ? 1 : 0) <= mesh.maxConnection // major filter, not too meaning at server for filter by number of conn of every purposes
				//  && countConnected() >= mesh.getQuota(clientId, ServerD.CONN_MAX) // minor filter TODO convert connectedChannels to map with client name
				&& ipConns.size() + (newEndpoint != null
				? (ipConns.containsKey(newEndpoint.ip) ? 1 : 0)
				: 0) <= mesh.maxHost // maxhost filter // then after accept a connection from Rhost, only accept conn from that host
			;
		logger.info("Server allow add conn {}", isAccept);
		return isAccept;
	}

	protected abstract Initer newServerInitializer(SHandler handler);

	//	/**
//	 * TCP usually use this
//	 *
//	 * @param handlers
//	 */
//	public void start(ServerHandler... handlers) {
//		List<String> errorString = new ArrayList<>();
//		ServerHandlers serverHandlers = ServerHandlers.newInstance();
//		for (ServerHandler handler : handlers) {
//			String version = ConnectionManager.checkAsyncHandlerSupportVersion(handler);
//			if (version == null)
//				errorString.add(String.format("Handler type %s passed to server %s is not supported", handler.getClass().getSimpleName(), config.id));
//			else serverHandlers.setProtoVersionHandler(version, handler);
//		}
//		if (!errorString.isEmpty()) throw new IllegalArgumentException(String.join("\n", errorString));
//		if (serverHandlers.size() > 0) start(serverHandlers);
//		// else skip
//	}
	public NettyServer<Conf, SHandler, Initer> restart(SHandler handlers) throws Exception {
		long startStop = TimeUtils.nowNano();
		stop();
		logger.info("Server {} at port {} stopped after {}", getId(), config.port, TimeUtils.miliPassed(startStop));
		// reset components for new run
		pause.set(false);
		counter.reset();
		return start(handlers);
	}

	public NettyServer<Conf, SHandler, Initer> start(SHandler handlers) throws Exception {
		if (isInitialized()) {
			logger.error("Server is already initialized");
			return this;
		}
		listen(config.getProtocolName(), config.host, config.port, handlers);
		return this;
	}
	public final void startWarmup(SHandler handlers) throws Exception {
		listen(config.getProtocolName() + " warmup", ChassisConst.WARMUP_HOST, config.port, handlers);
	}

	private void listen(String name, String host, int port, SHandler handlers) throws Exception {
		int startRetry = 0;
		boolean done = false;
		Exception lastException = null;
		Objects.requireNonNull(handlers, "Handler for server is require");
		logger.info("[Initializing server]{type={}, id={}, port={}}", name, config.id, config.port);

		Initer serverInitializer = Objects.requireNonNull(newServerInitializer(handlers), "ChannelInitiallizer for server is required");
		logger.info("[Starting server]{type={}, id={}, port={}}", name, config.id, port);
		do {
			try {
				bootstrap = remakeBoostrap().childHandler(serverInitializer);
				ChannelFuture bindFuture = host != null ? bootstrap.bind(host, port) : bootstrap.bind(port);
				boolean bindCompleted = bindFuture.await(10, TimeUnit.SECONDS);
				ch = bindFuture.channel(); // assign to ch to let stop() close channel
				if (!bindCompleted) {
					String mess = "Binding server port timeout. Cancelling ...";
					logger.error(mess);
					lastException = new TimeoutException(mess);
					bindFuture.cancel(true); // Note: This does not always interrupt the bind
				} else if (!bindFuture.isSuccess()) {
					logger.error("Binding server port failed. Cancelling ...", bindFuture.cause());
					lastException = new Exception(bindFuture.cause());
				} else {
					logger.info("{} server started at {}:{}", name, host, port);
					done = true;
					break;
				}
			} catch (Exception ex) {
				lastException = ex;
				logger.error(ex, ex);
				eventHandler.triggerEvent(new ServerEvent(ServerEvent.EventType.SERVER_FAIL_START, this, ex.getMessage()));
			}
			stop(); // all failed case close all to recreate new next round
		} while (startRetry++ < config.bindRetry && TimeUtils.waitSafeSec(5));
		if(!done) throw lastException;
	}

	protected boolean isInitialized() {
		return ch != null && ch.isActive();
	}


	protected void sendByChannelSharedBuffer(Channel channel, ChassisMgrableMsg msg, GenericFutureListener<ChannelFuture>... futures) {
		/*
		Need to overwrite this to add a future to release msg, becuz Res has no type, FullHttpResponse and Msg do not share any Releasable interface, so must call by future
		 */
		if (logger.isDebugEnabled())
			logger.debug("Server push to channel {}, msg {}", channel, msg);
		ReplyContext.sendByChannel(channel, msg, futures);
	}

	public void waitServerClose() {
		try {
			ch.closeFuture().sync();
		} catch (InterruptedException ignored) {
		}
	}

	public void stop() {
		notifyStop();
		try {
			shutdownServerChannel(ch);
			try {
				logger.info("[Shutdown server worker]{type={}, port={}}", config.getProtocolName(), config.port);
				if (workerGroup != null) workerGroup.shutdownGracefully().get(10, TimeUnit.SECONDS);
				logger.info("[Shutdown server eventloop]{type={}, port={}}", config.getProtocolName(), config.port);
				if (bossGroup != null) bossGroup.shutdownGracefully().get(10, TimeUnit.SECONDS);
			} catch (Exception e) {
				logger.error(e, e);
			}
			logger.info("{} server at {} stopped", config.getProtocolName(), config.port);
			negotiator.deregisterServer(getId());
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	private void shutdownServerChannel(Channel channel) {
		if (channel != null) {
			if (channel.parent() != null) {
				try {
					channel.parent().close();
					channel.parent().closeFuture().await(30, TimeUnit.SECONDS);
				} catch (Exception e) {
					logger.error(e, e);
				}
			}
			try {
				channel.close();
				channel.closeFuture().await(30, TimeUnit.SECONDS);
			} catch (Exception e) {
				logger.error(e, e);
			}
		}
	}

	public long countIp(String ip) {
		return connectedChannels.stream().map(CtxHolder::getIpFrom).filter(s -> Objects.equals(s, ip)).count();
	}

	@Override
	public Map<String, Long> countIps() {
		Map<String, Long> countMap = new HashMap<>();
		connectedChannels.stream().map(CtxHolder::getIpFrom).forEach(s -> countMap.compute(s, (k, v) -> (v == null ? 0 : v) + 1));
		return countMap;
	}
	@AllArgsConstructor
	public static class ServerLinkAddress{
		public final String clientIp;
		public final Endpoint serverE;

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof ServerLinkAddress)) return false;
			ServerLinkAddress o = (ServerLinkAddress) obj;
			return Objects.equals(clientIp, o.clientIp) && Objects.equals(serverE, o.serverE);
		}
	}
	@Override
	public Map<ServerLinkAddress, Integer /*count*/> getConnectedEndpointPair() {
		Map<ServerLinkAddress, Integer /*count*/> result = new HashMap<>();
		connectedChannels.forEach(c -> {
			ServerLinkAddress newLink = new ServerLinkAddress(CtxHolder.getIpFrom(c), Endpoint.local(c));
			Optional<ServerLinkAddress> existedKey = result.keySet().stream().filter(newLink::equals).findFirst();
			if(existedKey.isPresent()) result.compute(existedKey.get(), (k,v) -> (v == null) ? 1 : v + 1);
			else result.computeIfAbsent(newLink, v -> 1);
		});
		return result;
	}

	@Override
	public Map<Endpoint, Long> countEndpoints() {
		return connectedChannels.stream()
			.map(ChannelAttribute::getEndpoint)
			.filter(Objects::nonNull)
			.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
	}

	public String getConnectionStat() {
		return String.format("Server connections stat. id=%s, connected=%d, outboundBuffer={%s}",
			config.id, countConnected(), connectedChannels.stream()
				.map(f -> String.format("%s=%d/%d",
					f.remoteAddress(), f.unsafe().outboundBuffer().totalPendingWriteBytes(), f.unsafe().outboundBuffer().nioBufferSize()))
				.collect(Collectors.joining(";")));
	}

	public void waitFlushResponse(long maxWaitMs) {
		long remainFlushing = 0;
		long start = TimeUtils.nowNano();
		while (!TimeUtils.isTimeoutMili(start, maxWaitMs)
			&& (remainFlushing = connectedChannels.stream().filter(channel ->
			!channel.unsafe().outboundBuffer().isEmpty()
				|| channel.unsafe().outboundBuffer().totalPendingWriteBytes() > 0).count()) > 0
			&& TimeUtils.waitSafeMicro(100))
			connectedChannels.flush(); // neu chua timeout hoac allFlushed chua clear, van con = false -> tiep tuc wait
		if (remainFlushing > 0)
			logger.info("Server graceful flushed. id={}, time={}, stat={}", config.id, TimeUtils.miliPassed(start), getConnectionStat());
		else
			logger.warn("Server remain {} un-flushed connections. id={}, time={}, stat={}", remainFlushing, config.id, TimeUtils.miliPassed(start), getConnectionStat());
	}

	public String getId() {
		return config.id;
	}


	public void notifyStop() {
		logger.info("Server notify stop. id={}, port={}", config.id, config.port);
		pause.set(true);
	}


}
