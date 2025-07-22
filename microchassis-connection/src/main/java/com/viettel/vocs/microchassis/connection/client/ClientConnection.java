/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection.client;

import com.viettel.vocs.common.datatype.FuseBoolean;
import com.viettel.vocs.common.log.LogAccumulator;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisThreadManager;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.handler.ClientHandler;
import com.viettel.vocs.microchassis.connection.RequestTimeoutMonitor;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.event.ConnectionEvent;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.exception.ConnectionException;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.ManoConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.Routable;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.CircuitBreakerConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.ConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode.MonitorableConnectionMode;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.ClientLimiter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.client.HttpInstanceClient;
import com.viettel.vocs.microchassis.metrics.Metric;
import com.viettel.vocs.microchassis.metrics.MetricCollector;
import com.viettel.vocs.microchassis.tcp.client.TcpBasedClientConnection;
import com.viettel.vocs.microchassis.util.MonitorManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

interface Sendable<Req, Res> {
    boolean isConnected();

    void waitFlushAllMsg();

    Channel getChannel();

    void onIsolate(String message);// , Map<String, ?> connectionEventParam);

    String getChannelString();

    boolean isSendable();

    boolean isEnable();


    void close();

    /**
     * @param request: retained request, user must handle request.release yourself
     * @throws Exception
     */
    void send(Req request) throws Exception; // TODO ... control LB

    /**
     * @param request:  retained request, user must handle request.release yourself
     * @param timeoutMs
     * @return
     * @throws Exception
     */
    Res sendSync(Req request, long timeoutMs) throws Exception; // TODO ... control LB // TODO [tiennn18] implement finish count for Counter in LB
}

/**
 * @param <Req>  class của object gửi đi bởi client. Ví dụ HttpRequest, Msg
 * @param <Res>  class của object nhận về bởi client. Ví dụ HttpResponse, Msg
 * @param <Conf> class của object config extends ClientConfiguration. Ví dụ
 *               Http2ClientConfiguration
 * @author vttek
 */
public abstract class ClientConnection<Req extends ChassisMgrableMsg, Res extends ChassisMgrableMsg, Conf extends ClientConfiguration, CHandler extends ClientHandler>
        implements Routable, Sendable<Req, Res> {

    private static final Logger logger = LogManager.getLogger(ClientConnection.class);
    private static final AtomicLong idGenerator = new AtomicLong();
    protected final String monitorID;
    protected Channel nettyChannel;
    /**
     * @param enable: managing status, this match to Site.connectedChannels register and unregister
     * enable trigger continue to resolve request, vice versa with disable
     */
    @Getter
    protected boolean enable;
    @Getter
    @Setter
    protected boolean deleteMarker = false;

    @Override
    public boolean isDisableRoute() {
        return deleteMarker && getSentCC(false) == 0;
    }

    protected final AtomicBoolean writeException = new AtomicBoolean(false);
    /**
     * @param closed: absolute value, only change one when set connection close and cannot reverses
     */
    protected FuseBoolean closed = new FuseBoolean(false);
    protected final Conf config;
    protected final Endpoint endpointRepresent;
    @Getter
    protected final CHandler handler;
    protected final Bootstrap bootstrap; // only used in connect() method
    protected final EventHandler eventHandler;
    private final AtomicReference<RouteStrategy> routeStrategyRef;

    protected void waitFlushable(Channel channel) throws ConnectionException {
        int counter = 0;
        long start = TimeUtils.nowNano();
        channelWriteConcurrent.incrementAndGet(); // to notify conns select skip this conn, this conn is wait for flushing => 1 in netty buffer and this is 2, so any other send found that channelWriteConcurrent > 0, it is the 3rd of concurrency
        try {
            do {
                if (ChannelAttribute.getCounter(channel) == null)
                    throw new ConnectionException(String.format("Channel [%s-%s] down while wait flushing", endpointRepresent, channel), this, ConnectionException.CONN_ERROR.NO_CHANNEL);
                else if (!channel.isActive()) { // ve co ban, channel van la channel cua connection nay, nen van set enable va mark ready duoc
                    this.enable = false;
                    markAsNotReady();
                    throw new ConnectionException(String.format("Channel [%s-%s] is not active", endpointRepresent, channel), this, ConnectionException.CONN_ERROR.NO_CHANNEL);
                } else if (!channel.isWritable())
                    counter++; // chi can writable la msg vao flush duoc, block o select conn
                else return;
            } while (TimeUtils.waitSafeNano(config.channelConfiguration.parkWritableNano) && !TimeUtils.isTimeoutMili(start, config.channelConfiguration.maxChannelWritableMs));
            MonitorManager.getInstance().waitFlushClient(start);
            if (logger.isDebugEnabled())
                logger.debug("[x{}] Channel to {} not writable. channel={}, bytesBeforeUnwritable={}, bytesBeforeWritable={}",
                        counter, endpointRepresent, channel, channel.bytesBeforeUnwritable(), channel.bytesBeforeWritable());
            String message = String.format("[%s] Channel [%s-%s] not writable after %d tried for %.2fms/%dms",
                    config.id, channel, endpointRepresent, counter, TimeUtils.miliPassed(start), config.channelConfiguration.maxChannelWritableMs);
            if (!writeException.get() && !writeException.compareAndExchange(false, true))
                eventHandler.triggerEvent(new ConnectionEvent(ConnectionEvent.EventType.CONNECTION_WRITE_FAIL, this, message));
            throw new ConnectionException(message, this, ConnectionException.CONN_ERROR.CHANNEL_ERROR, ConnectionException.CONN_ERROR.TIMEOUT);
        } finally {
            channelWriteConcurrent.decrementAndGet();
        }
    }

    /**
     * @param channel
     * @param request   ham nay private, nen khi throw se co ham send khac la origin goi den ham nay catch exception, origin se xu ly release request.
     * @param listeners
     * @throws Exception
     */
    protected abstract void sendByChannel(Channel channel, Req request, long timeoutMs, GenericFutureListener<ChannelFuture>... listeners) throws ConnectionException;

    protected void watch(Channel channel, ChassisMgrableMsg request, long timeoutMs) {
        PeerCounter counter = ChannelAttribute.getCounter(channel);
        request.setSentTime();
        if (config.to3rdParty && !request.isMgr())
            request.setMessageId(null); // to 3rd party do not have response on msgId, so not send with mess_id header, then no watch on msgId
        else if (counter != null) {// send to chassis, or some 3rd party allow mess_id => watch as normal
            counter.watch(request.getMessageId(), request.getSentTime(), timeoutMs);
            if (logger.isDebugEnabled()) logger.debug("Watch for {} {}", request.getMessageId(), request);
        }
        MonitorManager.getInstance().entrustClient(request.getEntrustedStamp(), config.routeStrategy.logId);
    }

    protected void presend(Channel channel, Req request) throws ConnectionException {
        if (logger.isDebugEnabled())
            logger.debug("[{}] Execute beforeSendAction. id={}, msg={}, channel={}", endpointRepresent, config.id, request, channel);
        request.validate();
        waitFlushable(channel);
        if (logger.isDebugEnabled())
            logger.debug("[{}] Sending request. id={}, msg={}, channel={}", endpointRepresent, config.id, request, channel);
    }


    @Override
    public boolean checkAvailability() {
        return true; // default all conn is TCP -> async send any msg -> always available
        // TODO qualify this if we have UDP
    }

    public boolean equals(Endpoint endpoint) {
        return Objects.equals(endpoint, endpointRepresent);
    }

    private AtomicReference<PeerCounter> getCounterRef() { // do not change this to public, only allow access in connection, so null pointer all handled under CLientConnection methods
        return ChannelAttribute.getCounterRef(nettyChannel);
    }

    private AtomicReference<ClientLimiter> getLimiterRef() { // do not change this to public, only allow access in connection, so null pointer all handled under CLientConnection methods
        return ClientChannelAttribute.getLimiterRef(nettyChannel);
    }

    @Override
    public long getSentCC(boolean recent) {
        try {
            return recent ? getCounterRef().get().getRecentSentCC() : getCounterRef().get().getSentCC();
        } catch (NullPointerException ignored) {
            return Integer.MAX_VALUE; // while channel died
        }
    }

    protected Long lastPing = 0L;
    public int nServerConn = -1; // -1 mean can not ask

    public String getBaseServiceName() {
        return endpointRepresent.serviceName;
    }


    public Conf getConfig() {
        return config;
    }

    @Override
    public final Integer getPort() {
        return endpointRepresent.port;
    }


//	public ClientSendHandler getHandler(ChassisMgrableMsg msg) {
//		return getHandler(msg.getProtoVersionText());
//	}

//	public ClientSendHandler getHandler(String version) {
//		return handlers.get(version);
//	}


    @Override
    public void onIsolate(String message) {// , Map<String, ?> connectionEventParam) {
        logger.error(message);
        eventHandler.triggerEvent(new ConnectionEvent(ConnectionEvent.EventType.TIMEOUT_ISOLATION, this, message));//.setParams(connectionEventParam));
    }

    protected final Site<Conf> ownerSite;


    private long waitBeforeUseMs = 0;
    public final AtomicBoolean usable = new AtomicBoolean(true); // init false
    protected long lastConnectedTime = 0;
    protected final ClientChannelInitializer<Conf, CHandler> initializer; // only 1 instance share among renew channels

    protected ClientChannelInitializer<Conf, CHandler> getInitializer() {
        return initializer;
    }

    public boolean checkUsable() {
        // flip while checked
        if (!usable.get() && TimeUtils.isTimeoutMili(lastConnectedTime, waitBeforeUseMs))
            usable.set(true);
        return usable.get();
    }

    public boolean checkAndClaimUsable() {
        // flip while checked
        synchronized (usable) {
            if (checkUsable()) {
                usable.set(false);
                return true; // true for called thread
            } else return false;
        }
    }

    protected abstract ClientChannelInitializer<Conf, CHandler> newClientInitializer(MsgCounter msgCounter);

    @Getter
    protected final MsgCounter msgCounter;

    protected ClientConnection(Conf config, Site<Conf> ownerSite, EventHandler eventHandler, Endpoint endpoint, CHandler handler, Bootstrap bootstrap, AtomicReference<RouteStrategy> routeStrategyRef) {
        /*
        Khong implement warmup tai lib chassis, lib chassis chi dam nhiem viec ket noi,
         viec warm up, quy dinh cac buoc warmup va luong warmup nhung API nao do la viec cua service nghiep vu,
         lib chassis khong the biet duoc
         */
        logger.info("Creating {} connection. clientId={}, endpoint={}", config.getProtocolName(), config.id, endpoint);
        this.ownerSite = ownerSite; // dung de ChannelMonitor.doCheck onDown->site.deregister, onNew->site.register
        this.handler = handler;
        this.config = config;

        this.eventHandler = eventHandler;
        this.routeStrategyRef = routeStrategyRef;
        this.bootstrap = bootstrap;
        float siteWeight = ManoConfigure.getWeightFromManoConfig(ownerSite.getHostName());
        if (siteWeight > 0) {
            waitBeforeUseMs = ChassisConfig.ManoConfig.manoGraceWait.get();
            usable.set(false);
        }
        this.lastConnectedState = new AtomicBoolean(false); // false because have not init to connect
        this.state = new AtomicReference<>(RouteStrategy.CONNECTION_STATUS.NOT_READY); // false because have not init to connect
        // strategy read from outside so no need to keep strategy inside each connectionWrapper
        this.endpointRepresent = endpoint;
        this.monitorID = config.id + "_" + endpointRepresent + "_" + idGenerator.incrementAndGet();
        if (ownerSite instanceof HttpClient) this.msgCounter = ((HttpClient) ownerSite).getMsgCounter();
        else if (ownerSite instanceof HttpInstanceClient)
            this.msgCounter = ((HttpInstanceClient) ownerSite).getMsgCounter();
        else this.msgCounter = MsgCounter.ofClient(config.id, monitorID);

        this.initializer = newClientInitializer(msgCounter);
        logger.info("Connection {} use initer {}", System.identityHashCode(this), System.identityHashCode(initializer));
        this.bootstrap.handler(initializer);
    }

    protected abstract void onChannelReady(Channel channel, long startConnect) throws Exception;

    private void onConnected(@NotNull Channel channel, long startConnect) throws Exception { // ra den day la co channel roi
        int timeoutMiliseconds = config.channelConfiguration.timeoutConnectMs;

        LogAccumulator channelNotWritableLA = new LogAccumulator((a, c) -> {
        }, c -> logger.info("[{}] Wait for channel writable (x{})", endpointRepresent, c.getAccumulated()));
        while ((!channel.isWritable() || !channel.isActive())) {
            if (TimeUtils.isTimeoutMili(startConnect, timeoutMiliseconds)) {
                logger.error("[{}} Can not write create request to channel (id:{}) after {}ms. Connection initialized failed but created. Close channel then recreate later.", endpointRepresent, config.id, TimeUtils.miliPassed(startConnect));
                markAsNotReady();
                channel.close();
                return;
            }
            channelNotWritableLA.accumulate();
            TimeUtils.waitSafeMili(1);
        }
        channelNotWritableLA.logIfAccumulated();
        // ra den day writable
        onChannelReady(channel, startConnect);
        onChannelRenew(channel);
        logger.info("[{} connection initialized successfully]{id={}, channel={}}", config.getProtocolName(), config.id, channel);
        lastConnectedTime = TimeUtils.nowNano();
        triggerRegister();
    }

    @Override
    public synchronized void connect(int maxRetry) throws ConnectionException {
        Channel channel;
        logger.info(String.format("[Initializing %s connection]{id=%s, endpoint=%s}", config.getProtocolName(), config.id, endpointRepresent));
        for (int i = 0; i < maxRetry; i++) {
            try {
                long startConnect = TimeUtils.nowNano();
//				bootstrap.config().options().put(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.channelConfiguration.timeoutConnectMs);
                ChannelFuture future = bootstrap.connect(endpointRepresent.ip, endpointRepresent.port);
                logger.info("[{}] Wait for channel", endpointRepresent);
//				future.addListener((ChannelFutureListener) f -> {
//					if (!f.isSuccess()) { // Connection failed or timed out
//						Throwable cause = f.cause();
//						if (cause instanceof java.util.concurrent.TimeoutException) {
//							logger.error("[{}] Can not create channel (id:{}) after {}ms. Connection initialized failed but created. Channel recreate later.",
//								endpointRepresent, config.id, TimeUtils.miliPassed(startConnect));
//						} else {
//							logger.error("[{}] Connect failed (id:{}) after {}ms. Connection initialized failed but created. Channel recreate later. Caused by: {}",
//								endpointRepresent, config.id, TimeUtils.miliPassed(startConnect), cause);
//						}
//					} else { // Connection succeeded
//						onConnected(f.channel(), startConnect);
//					}
//				});
                boolean isSuccess = future.await(config.channelConfiguration.timeoutConnectMs, TimeUnit.SECONDS);
                channel = future.channel();
                if (!isSuccess) { // Timed out
                    logger.error("[{}] Can not create channel (id:{}) after {}ms. Connection initialized failed but created. Channel recreate later.", endpointRepresent, config.id, TimeUtils.miliPassed(startConnect));
                    future.cancel(true); // optional: cancel the future
                } else if (future.isSuccess() && channel != null) { // Connection succeeded
                    onConnected(channel, startConnect); // this connect at socket level, but maybe dont have server to answer
                    return;
                } else { // Connection failed
                    logger.error("[{}] Connect failed (id:{}) after {}ms. Connection initialized failed but created. Channel {} recreate later. {} caused by: {}", endpointRepresent, config.id, TimeUtils.miliPassed(startConnect), future.channel(), future.isSuccess(), future.cause());
                }
                cleanupMsgCounter();
                if (channel != null) channel.close(); // close for eventloops hang 100% CPU
            } catch (Exception e) {
                logger.error(e, e);
            }
        }
        throw new ConnectionException(String.format("Can not create %s channel to %s for client id %s", config.getProtocolName(), endpointRepresent, config.id), this, ConnectionException.CONN_ERROR.INIT_CONNECT);
    }

    // #Sample
    protected void init() { // init = ToCheck.re conn.onChannelRenew(conn.connect())
        try {
            connect(Math.max(config.maxRetryConnect, 1));
        } catch (ConnectionException ex) {
            logger.error(ex);
        } catch (Exception ex) {
            logger.error(ex, ex);
        }
        if (!TimeUtils.completeUnder(() -> nettyChannel != null && nettyChannel.isActive(), config.pingTimeoutMs))
            logger.debug("First create get null channel");
    }

    public void triggerRegister() {
        if (!ownerSite.register(this))
            ownerSite.revokeOverCreated(this);
    }


    public Endpoint getEndpoint() {
        return endpointRepresent;
    }

    @Override
    public final String getHostName() {
        return endpointRepresent.ip;
    }

    public String ip() {
        return getHostName();
    }

    public int port() {
        return getPort();
    }

    @Override
    public boolean isWritable() {
        return nettyChannel != null && nettyChannel.isWritable();
    }


    final AtomicReference<RouteStrategy.CONNECTION_STATUS> state;

    public AtomicReference<RouteStrategy.CONNECTION_STATUS> getState() {
        return state;
    }

    @Override
    public ConnectionMode getConnMode() {
        return ClientChannelAttribute.getConnMode(nettyChannel);  // always have an active CM, setted at channel initer, before all ref to this nettyChannel
    }

    public AtomicReference<ConnectionMode> getConnModeRef() {
        return ClientChannelAttribute.getConnModeRef(nettyChannel);
    }

    final AtomicBoolean lastConnectedState;

    @Override
    public boolean isInDnsResolve() {
        return ownerSite.resolvedContainIp(endpointRepresent.ip);
    }

    public final boolean isConnected() { // not allow to override
        boolean newState = enable
                && !closed.get()
                && Routable.isConnected(nettyChannel);
        if (newState != lastConnectedState.getAndSet(newState) // is changed // trigger on state change so not too much compute
                || (newState && RouteStrategy.CONNECTION_STATUS.NOT_READY.equals(state.get())) // detect change in state to ready or warmable
            // else neu nhu conn dang ready hoac warming se co cac exception va check channel de chuyen ngay lap tuc ve NotReady khi gui -> ko can check o day
        ) {
            RouteStrategy strategy = routeStrategyRef.get();
            if (newState) strategy.classify(this);
            else strategy.markAsNotReady(this);
        }
        return newState;
    }

    @Override
    public boolean isSendable() {
        return isConnected() && isWritable();
    }

    @Override
    public synchronized void setEnable(boolean b) {
        this.enable = b;
        if (!b) {
            long remain = 0;
            long sent = 0;
            AtomicReference<PeerCounter> counterRef = getCounterRef();
            if (counterRef != null) {
                sent = counterRef.get().getSent();
                remain = counterRef.get().getSentCC();
            }
            logger.info("Connection disable. id={}, endpoint={}, channel={}, sent/timeoutWait={}/{}",
                    config.id, endpointRepresent, nettyChannel, sent, remain);
        }
    }

    public synchronized void setClientChannel(Channel newChannel) {
        this.nettyChannel = newChannel;
    }


    @Override
    public void deregister() {
        setDeleteMarker(true);
        ownerSite.deregister(this, true);
    }

    public synchronized void close() { // need synchronized bcz Channel check can renew connection while closing to create unregisterd conn but still active
        if (logger.isDebugEnabled())
            logger.info("[{}] Start closing connection. id={}", endpointRepresent, config.id);
        markAsNotReady();
        endpointRepresent.failedToConnect(); // to reset for other ServiceMonitor claim to create new connect, this endpoint live independan with connection, new connection can be create and attach this endpoint again, if not reset connection claim, this NettyClient/Site cannot create any connection to this endpoint bcause endpoint is distincted by resolvedIPs
        try {
            closed.fuse(true);
            setEnable(false);
            if (logger.isDebugEnabled())
                logger.info("Unregister channel monitor. conn={} id={}, ip={}, port={}", nettyChannel, this.config.id, this.getHostName(), this.getPort());
            ChannelMonitor.getInstance().unRegisterMonitorObject(this, null);
            long startWait = TimeUtils.nowNano();
            if (logger.isDebugEnabled())
                logger.info("Start wait graceful before close timeout/counter monitor. conn={} id={}, ip={}, port={}", nettyChannel, this.config.id, this.getHostName(), this.getPort());
            waitFlushAllMsg();
            float miliPassed = TimeUtils.miliPassed(startWait);
            if (logger.isDebugEnabled() || miliPassed > 1)
                logger.info("End graceful wait after {}ms. conn={} id={}, ip={}, port={}", miliPassed, nettyChannel, this.config.id, this.getHostName(), this.getPort());
            ClientLimiter limiter = getLimiter();
            if (limiter != null) limiter.close();
            cleanupMsgCounter();
        } catch (Exception e) {
            logger.error(e, e);
        }
    }

    private void cleanupMsgCounter() {
        if (this instanceof TcpBasedClientConnection) {
            String id = MsgCounter.makeClientName(getClientId(), getMonitorID());
            try {
                MsgCounter remove = MsgCounter.counterMap // counter of httpclient -> no remove, tcp counter of each conn => remove on close
                        .remove(id);
                if (remove == null) {
                    logger.error("Can not find MsgCounter metric. id={}", id);
                    return;
                }
                Metric metric = MetricCollector.getMetric();
                if (metric == null) {
                    logger.error("Can not find Metric from MetricCollector");
                    return;
                }
                remove.removeMetrics(metric);
            } catch (Exception e) {
                logger.error("Error cleanupMsgCounter id = {}", id);
                logger.error(e, e);
            }
        }
    }

    public void waitFlushAllMsg() {
        TimeUtils.completeUnder(() -> nettyChannel == null || getSentCC(false) == 0, config.sendTimeoutMs); // wait before remove limiter->counter.close() of timeout monitor
    }


    public void markAsNotReady() { // when channel null, recheck null again before set it channel is recreated concurrently
        if (nettyChannel == null) ownerSite.getRouteStrategyRef().get().markAsNotReady(this);
    }


    @Override
    public void onChannelDown() {
        String message;
        markAsNotReady();
        ConnectionEvent.EventType event = ConnectionEvent.EventType.CONNECTION_DOWN;
        if (nettyChannel == null) {
            message = "Connection to service name " + config.host + ", client ID " + config.id + ". Can not create connection to " + endpointRepresent;
            event = ConnectionEvent.EventType.CONNECTION_INIT_FAIL;
        } else {
            message = String.format("Connection to service name %s, client ID %s down. channel=%s, enable=%s, isActive=%s, isOpen=%s, isWritable=%s}", config.host, config.id, nettyChannel, enable, nettyChannel.isActive(), nettyChannel.isOpen(), isWritable());
            Set<String> remainMsgIds = RequestTimeoutMonitor.unregister(nettyChannel);
            setEnable(false);
            if (remainMsgIds != null && !remainMsgIds.isEmpty()) {
                CompletableFuture.runAsync(() -> { // invoke chassisTimeoutHandle -> use app response handle threadpool
                    logger.info("[Trigger timeout handler on down channel]{clientID={}, channel={}, count={}}", config.id, nettyChannel, remainMsgIds.size());
                    for (String msgId : remainMsgIds) {
                        try {
                            getHandler().chassisTimeoutHandle(msgId);
                        } catch (Exception ex) {
                            logger.error(ex, ex);
                        }
                    }
                }, ChassisThreadManager.tcHandlePool);
            }
            try {
                nettyChannel.close();
            } catch (Exception ex) {
                logger.error(ex, ex);
            }
        }
        logger.error(message);
//		ConnectionEventParam param = ;
//		if (lastPing > 0) {
//			param.getParams().put("last_ping", lastPing);
//		}
        eventHandler.triggerEvent(new ConnectionEvent(event, this, message));
    }


    @Override
    public void processTimeoutMsgId(Channel sendChannel, String msgId) {
        PeerCounter counter = ChannelAttribute.getCounter(sendChannel);
        if (counter != null) counter.timeout(msgId); // acked in here
    }

    @Override
    public void onChannelRenew(Channel newChannel) {
        if (newChannel == null) markAsNotReady();
        else {
            newChannelReady();
            ownerSite.getRouteStrategyRef().get().classifyConnection(this);
            eventHandler
                    .triggerEvent(new ConnectionEvent(ConnectionEvent.EventType.CONNECTION_UP, this))
                    .triggerEvent(new ConnectionEvent(ConnectionEvent.EventType.CONNECTION_INIT_SUCCESS, this));
        }
    }


    protected abstract void newChannelReady();


    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public final Channel getChannel() {
        return nettyChannel;
    }

    @Override
    public final String getChannelString() {
        return nettyChannel != null ? nettyChannel.toString() : "null";
    }


    @Override
    public String getMonitorID() {
        return monitorID;
    }

    public String getId() {
        return getMonitorID();
    }


    @Override
    public ConnectionMode skipWarmUp() {
        ClientLimiter limiter = getLimiter();
        if (nettyChannel != null && limiter != null && (limiter.warmingUpTPS() > 0)) { // check if this ConnectionWrapper is WarmupMode and do ing warming up
            logger.info("Skip warm up for {}", getId());
            ConnectionMode newMode = routeStrategyRef.get().newReadyMode(nettyChannel.id().toString());
            getConnModeRef().set(newMode);
            return limiter.setMode(newMode);

        }
        return null;
    }

    public int getPeerLoad() {
        ConnectionMode connMode = getConnMode();
        return connMode instanceof MonitorableConnectionMode ? ((MonitorableConnectionMode) connMode).getPeerLoad() : 0;
    }

    @Override
    public ConnectionMode checkMode() {
        ClientLimiter limiter = getLimiter();
        ConnectionMode curMode;
        if (limiter != null && (curMode = limiter.getMode()).isEnd()) {
            logger.info("Done {} for {}", limiter.getMode().getClass().getSimpleName(), getMonitorID());

            return limiter.setMode(
                    curMode instanceof CircuitBreakerConnectionMode
                            && limiter.isolationEngine != null
                            ? limiter.isolationEngine.doneTestBringBackOldMode(this)
                            : routeStrategyRef.get().newReadyMode(nettyChannel.id().toString()));
        }
        return null;
    }

    public int warmingUpTPS() {
        ClientLimiter limiter = getLimiter();
        return limiter != null ? limiter.warmingUpTPS() : -1;
    }

    @Override
    public PeerCounter getCounter() {
        return ChannelAttribute.getCounter(nettyChannel); // might be null if channel died
    }

    @Override
    public ClientLimiter getLimiter() {
        AtomicReference<ClientLimiter> ref = getLimiterRef();
        return ref != null ? ref.get() : null; // might be null if channel died
    }

    protected final AtomicInteger channelWriteConcurrent = new AtomicInteger(0);

    private boolean isAllowAccquired() {
        return isConnected() && channelWriteConcurrent.get() == 0;
    }

    public void acquire(TimeUtils.NanoBeacon startBeacon) throws TimeoutException {
        ClientLimiter limiter = getLimiter();
        if (limiter != null) {
            limiter.acquire(startBeacon);
            while (!isAllowAccquired() && !startBeacon.isTimeout())
                TimeUtils.waitSafeNano(config.channelConfiguration.parkWritableNano); //  		dang co 2 msg(2 thread) vao luong isWritable fail -> flush() //			ngay khi 1 thread write thanh cong, thi thread nay release wait de di vao send, validate, create promise cac thu ... ma khong tao ra thoi gian cho qua nhieu
        } // else neu channel null, thi cho thong qua that nhanh de di den luong send va ban exception null channel sau
    }

    public boolean tryAccquire() {
        ClientLimiter limiter = getLimiter();
        return limiter != null && limiter.tryAcquire() && isAllowAccquired();
    }

    public List<Integer> extractTpsWarmupSteps(String tpsString) {
        if (tpsString != null && !tpsString.isEmpty()) {
            String[] steps = tpsString.split(",");
            if (steps.length > 0) {
                return Arrays.stream(steps)
                        .map(stepString -> {
                            try {
                                return Integer.parseInt(stepString.trim());
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }

    public String getClientId() {
        return config.id;
    }

}
