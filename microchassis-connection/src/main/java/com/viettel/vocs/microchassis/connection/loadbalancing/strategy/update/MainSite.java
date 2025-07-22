package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update;

import com.viettel.vocs.common.datatype.CollectionUtils;
import com.viettel.vocs.common.datatype.FuseBoolean;
import com.viettel.vocs.common.os.thread.NameableThreadFactory;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.microchassis.base.ChassisThreadManager;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.handler.ClientHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.client.ClientConnection;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.dns.HostNameResolver;
import com.viettel.vocs.microchassis.connection.event.ContextEvent;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.event.InternalEventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinityThreadFactory;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @param <Req>  class của object gửi đi bởi client. Ví dụ HttpRequest, Msg
 * @param <Res>  class của object nhận về bởi client. Ví dụ HttpResponse, Msg
 * @param <Conf> class của object config extends ClientConfiguration. Ví dụ
 *               Http2ClientConfiguration
 * @author vttek
 */
public abstract class MainSite<
	Req extends ChassisMgrableMsg,
	Res extends ChassisMgrableMsg,
	Conf extends ClientConfiguration,
	CHandler extends ClientHandler>
	extends Site<Conf> {
	protected static final Logger logger = LogManager.getLogger(MainSite.class);
	protected final FuseBoolean nettyInitialized = new FuseBoolean(false);
	protected CHandler handler; // after constructor is null safe
	protected final String type = config.getProtocolShortName();
	public final EventHandler eventHandler = new InternalEventHandler(config.id, ChassisThreadManager.makeEventPool(String.format("c-%s", config.id))); // each peer has 1 event pool to isolate if peer blocked
	protected final ByteBufAllocator bytebufAllocator = config.channelConfiguration.pooledByteBuffer
		? new PooledByteBufAllocator(config.channelConfiguration.preferDirect)
		: new UnpooledByteBufAllocator(config.channelConfiguration.preferDirect); // each client has 1 allocator


	/**
	 * part for define LB
	 */
	protected ThreadManager.PoolExecutor lbMonitor = ThreadManager.newFixedThreadPool(2, new NameableThreadFactory("LBmon"));
	protected final Bootstrap bootstrap;
	protected final MultithreadEventLoopGroup workerGroup;
	protected final List<Runnable> pressureActions = new CopyOnWriteArrayList<>();
	public MainSite<Req, Res,Conf, CHandler> removePressureAction(Runnable action){
		pressureActions.remove(action);
		return this;
	}
	public MainSite<Req, Res,Conf, CHandler> onPressure(Runnable action){
		pressureActions.add(action);
		return this;
	}
	public boolean isUnderPressure() {
		RouteStrategy strategy = routeStrategyRef.get();
		return strategy != null && strategy.isPressure();
	}
	protected <Targ> Map<String /*IP*/, Pair<Req, List<Targ>>> makeBroadcastJobs(Collection<Targ> targets, Req message) {
		byte[] content = message.getContent();
		if (logger.isDebugEnabled())
			logger.info("Broadcast original static content of length " + content.length + "B");
		Map<String, List<Targ>> destMapByIp = targets.stream().collect(Collectors.groupingBy((Function<? super Targ, String>) target -> {
			if (target instanceof String) return (String) target;
			else if (target instanceof ClientConnection)
				return ((ClientConnection<?, ?, ?, ?>) target).getEndpoint().ip;
			else return target.toString();
		}));

		Map<String, Pair<Req, List<Targ>>> mapJob = destMapByIp.entrySet().stream().collect(
			// map of each ip, has 1 job, contain the job Req, and list connected targets, send by first success
			Collectors.toMap(Map.Entry::getKey, job -> {
				Req newMsg = (Req) message.replicate();
				newMsg.setMessageId(newMsg.getMessageId() + "_" + ChassisMgrableMsg.newMsgId());
				return Pair.of(newMsg, job.getValue());
			}));
		if (logger.isDebugEnabled())
			logger.info("Preparing broadcast {}B to {}", content.length, mapJob.entrySet().stream().map(job ->
				String.format("%dB->%s{%s}", job.getValue().getKey().readableBytes(), job.getKey(), job.getValue().getValue().stream().map(conn -> {
				if (conn instanceof String) return (String) conn;
				else if (conn instanceof ClientConnection)
					return ((ClientConnection<?, ?, ?, ?>) conn).getMonitorID();
				else return conn.toString();
			}).collect(Collectors.joining(";")))).collect(Collectors.joining(" % ")));
		return mapJob;
	}

	protected void checkInit() throws IllegalStateException {
		/**
		 * Tat ca cac hanh dong tren connection deu can init truoc, vi connection duoc tao bang init->initDNS->createConn
		 */
		if (!nettyInitialized.get())
			throw new IllegalStateException("Client " + getId() + " must be initialized before using");
	}

	@Override
	public ByteBufAllocator getBytebufAllocator() {
		return bytebufAllocator;
	}

	protected MainSite(Conf config) {
		super(config);
		config.mapDnsIp.forEach((dns, ips) -> { // fill dns for grand mapDnsIp
			Set<String> seededIps = HostNameResolver.mapDnsIp.get(dns);
			CollectionUtils.DiffResult<String> diff;
			if (seededIps == null) HostNameResolver.addIpForDns(dns, ips);
			else if (!(diff = CollectionUtils.diff(seededIps, ips)).isEqual)
				HostNameResolver.addIpForDns(dns, diff.appear); // appear
		});
		ConnectionManager.mapClient.putIfAbsent(this.config.id, this);
		// register based eventHandlers
		eventHandler.addEventHandler(ContextEvent.EventType.CHANNEL_ACTIVE, (Consumer<ContextEvent>) event -> {
			// khong can bat su kien classifyConnection o day, vi sau do da goi o createConnection/connect
			logger.info("[{}] New channel opened {}", getId(), event.getChannel());
		}).addEventHandler(ContextEvent.EventType.CHANNEL_INACTIVE, (Consumer<ContextEvent>) event -> {
			logger.info("[{}] Channel down {}", getId(), event.getChannel());
		});
		// setting
		ThreadFactory threadFactory = config.channelConfiguration.affinity
			? new AffinityThreadFactory("ac" + type + "w_" + config.id, AffinityStrategies.DIFFERENT_CORE)
			: new BasicThreadFactory.Builder()
			.namingPattern("dc" + type + "w_" + config.id + "-%d")
			.daemon(true)
			.priority(Thread.MAX_PRIORITY)
			.wrappedFactory(Executors.defaultThreadFactory())
			.build();
		if (!Epoll.isAvailable()) logger.info("Epoll is not supported. Use NioEventLoopGroup");
		boolean epoll = config.channelConfiguration.epoll && Epoll.isAvailable();
		workerGroup = epoll
			? new EpollEventLoopGroup(config.channelConfiguration.numWorkerThread, threadFactory)
			: new NioEventLoopGroup(config.channelConfiguration.numWorkerThread, threadFactory);
		logger.info(String.format("Use %d worker thread for client", config.channelConfiguration.numWorkerThread));
		bootstrap = new Bootstrap();
		bootstrap.group(workerGroup)
			.channel(epoll ? EpollSocketChannel.class : NioSocketChannel.class)
			.option(ChannelOption.TCP_NODELAY, true)
			.option(ChannelOption.SO_REUSEADDR, true)
			.option(ChannelOption.SO_KEEPALIVE, true)
			.option(ChannelOption.SO_SNDBUF, config.channelConfiguration.channelSoSndBuf)
			.option(ChannelOption.SO_RCVBUF, config.channelConfiguration.channelSoRcvBuf)
			.option(ChannelOption.AUTO_READ, true)
			.option(ChannelOption.ALLOCATOR, bytebufAllocator)
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.channelConfiguration.timeoutConnectMs);

		if (config.channelConfiguration.backLog > 0)
			bootstrap.option(ChannelOption.SO_BACKLOG, config.channelConfiguration.backLog);
		//Connection event should be executed in order
	}


	/**
	 * this method for child of nettyclient override, not for interface to expose and callable
	 * use ServiceDiscoverable.createConnection(...) instead
	 * with specified readyMode, only for define API call to create, external call wrapped createConnection above
	 */
	public abstract ClientConnection<Req, Res, Conf, CHandler> createConnection(Site<Conf> ownerSite, Endpoint endpoint, EventHandler eventHandler, boolean monitor);

	/**
	 * with custom handler
	 */
	public abstract ClientConnection<Req, Res, Conf, CHandler> createConnection(Site<Conf> ownerSite, Endpoint endpoint, CHandler handler, EventHandler eventHandler, boolean monitor);
}
