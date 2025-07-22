/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.http.client;

import com.viettel.vocs.common.hashing.CheckSumEngine;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.base.ChassisThreadManager;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.codec.context.CtxHolder;
import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.connection.RequestTimeoutMonitor;
import com.viettel.vocs.microchassis.connection.client.ClientConnection;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.exception.ConnectionException;
import com.viettel.vocs.microchassis.connection.exception.SendTimeout;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.RouteStrategy;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.MsgCounter;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.codec.io.H1CIHandler;
import com.viettel.vocs.microchassis.http.codec.io.InboundHttp2ToHttpAdapter;
import com.viettel.vocs.microchassis.http.config.Http2ChannelConfigure;
import com.viettel.vocs.microchassis.http.config.HttpClientOptions;
import com.viettel.vocs.microchassis.util.MonitorManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.*;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http2.Http2CodecUtil.FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.PING_FRAME_PAYLOAD_LENGTH;
import static io.netty.handler.codec.http2.Http2FrameTypes.PING;

/**
 * @author vttek
 */
public class HttpClientConnection extends ClientConnection<HttpRequest, HttpResponse, HttpClientOptions, HttpClientHandler> {
	private static final Logger logger = LogManager.getLogger(HttpClientConnection.class);
	static Http2Flags flags = new Http2Flags();
	protected HttpScheme scheme;
	protected final SslContext sslCtx;
	private final AtomicBoolean claimingStreamId = new AtomicBoolean(false); // because stream id is predicted when send so need lock next get
//	protected final ClientHandlers sendHandlers; // need this, generally for HTTP1 (each connection send sync) and HTTP2 each connection can send multiple messages -> Map

	public HttpClientConnection(SslContext sslCtx, HttpClientOptions config, Site<HttpClientOptions> ownerSite, EventHandler eventHandler, Endpoint endpoint, HttpClientHandler handler, Bootstrap bootstrap, AtomicReference<RouteStrategy> routeStrategy) {
		super(config, ownerSite, eventHandler, endpoint, handler, bootstrap, routeStrategy);
		this.scheme = config.scheme();
		this.sslCtx = sslCtx;
		enable = true;
		init();
	}

	@Override
	protected void onChannelReady(Channel channel, long startConnect) throws Exception {
		// verifyChannelPipeline
		H1CIHandler h1CIHandler = channel.pipeline().get(H1CIHandler.class);
		if (h1CIHandler == null) return;
		if (!h1CIHandler.getHandler().equals(this.handler)) {
			logger.warn("Mismatch handler. id={}, fromPipeline={}, fromUser={}", channel, h1CIHandler.getHandler(), this.handler);
			//TODO: set expected handler again for H1CIHandler
			//h1CIHandler.setUserHandler(this.handler);
		}

		if (config.http2.isUpgradeHttp2)
			HttpClientInitializer.awaitSettings(channel, config.http2.http2AwaitSettingSeconds, TimeUnit.SECONDS);
	}

	@Override // this is send sync
	protected void sendByChannel(Channel channel, HttpRequest request, long timeoutMs, GenericFutureListener<ChannelFuture>... listeners) throws ConnectionException {
		presend(channel, request);
		request
			.setHeader(HttpHeaderNames.HOST, endpointRepresent.toFormalString())
			.setHeader(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(request.readableBytes()))
			.addHeader(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme.toString())
			.addHeader(HttpHeaderNames.USER_AGENT, ChassisConfig.ConnectionConfig.HttpConfig.USER_AGENT.get())
			.addHeader(HttpHeaderNames.CONNECTION, HttpHeaderNames.KEEP_ALIVE);
		CheckSumEngine checkSumEngine = config.getCheckSumEngine();
		if (checkSumEngine != null) request.setHeader(
			checkSumEngine.getHttpHeaderKey(),
			checkSumEngine.hash(request.getReadOnlyContentByteBuf()));
		if (logger.isDebugEnabled())
			logger.debug("Before flush sending HTTP channel={}, headers={}, request = {}", request.report(), channel, request.headers());

		//TODO convert to default
		ChannelPromise channelPromise = channel.newPromise();
		if (listeners != null) channelPromise.addListeners(listeners);
		channelPromise.addListener(f -> checkOverCreateStreamId());
		if (CtxHolder.isHttp2(channel))
			channelPromise.addListener(f -> claimingStreamId.set(false)); // after flush, allow next claim
		watch(channel, request, timeoutMs);
		long startFlush = TimeUtils.nowNano();
		channel.writeAndFlush(request, channelPromise.addListener(f -> MonitorManager.getInstance().clientFlush(startFlush, config.routeStrategy.logId)));
	}

	public boolean isHttp2() {
		return nettyChannel != null && CtxHolder.isHttp2(nettyChannel);
	}

	public boolean isHttp1() {
		return nettyChannel != null && CtxHolder.isHttp1(nettyChannel);
	}

	private static final byte[] pingPayload = new byte[8];
	private final Http2ChannelConfigure h2config = Objects.requireNonNull(config.http2, "HTTP2 connection required support HTTP2 config");

	/**
	 * Http1 send sync on channel -> dont have Timeout map on PeerCounter, it only recordSend and recordResponse. but conn create continuously -> only count at handler or simulator
	 */
	static AtomicInteger rcvCnt = new AtomicInteger();
	static AtomicInteger completeCnt = new AtomicInteger();

	public void responseAndAck(ChannelHandlerContext ctx, HttpResponse httpResponse) {
		/** Nhieu ban tin co the ve client cung 1 luc, do do trong 1 thoi diem chi co 1 ban tin duoc gui di, cho ket qua ve
		 */
//		if (logger.isDebugEnabled())
		Channel ch = ctx.channel();
		ChannelId cid = ch.id();
		if (logger.isDebugEnabled()) logger.debug("responseAndAcked {}", rcvCnt.incrementAndGet());
		HttpClientReceiveContext hctx = HttpClientReceiveContext.wrapCtx(ctx, httpResponse);
		if (logger.isDebugEnabled())
			logger.info("Channel {} {} receive response {} headers {} content {} utf8content {}", hctx.isHttp2() ? String.format("H2 stream %d", hctx.streamId) : "H1", cid, httpResponse.status(), httpResponse.headers(), httpResponse.content(), httpResponse.toStringUTF8());
		HttpIncompleteFuture<HttpClientReceiveContext> chassisHttpClientFuture = null; //  = HttpClientChannelAttribute.popFuture(nettyChannel);
		AtomicReference<Pair<String/*msgId*/, HttpIncompleteFuture<HttpClientReceiveContext>>> resultSetRef = getSentMsgId.apply(ch, hctx.streamId);
		Pair<String/*msgId*/, HttpIncompleteFuture<HttpClientReceiveContext>> resultSet = null;
		if (resultSetRef != null && (resultSet = resultSetRef.getAndSet(null)) != null) {
			ack(resultSet.getLeft()); // must call after getIncompleteFuture
			chassisHttpClientFuture = resultSet.getRight();
			if (chassisHttpClientFuture != null)
				hctx.getInMsg().setSentTime(chassisHttpClientFuture.getSentTime() /*always have long number > 0*/);
			if (hctx.getInID() == null || hctx.getInID().isEmpty())
				hctx.getInMsg().setMessageId(resultSet.getLeft()); // set if havent set msgId
		}
		// close conn after record all information of channel ^^^^^^^^^
		httpResponse.onHeaderNotNull(HttpHeaderNames.CONNECTION.toString(), connectionHeader -> {
			if (CLOSE.toString().equals(connectionHeader)) {
				if (logger.isDebugEnabled())
					logger.debug("Close as server asked. channel={}", ch);
				deregister(); // hoac trigger eventHandler INACTIVE_CHANNEL
			}
		});
		// close conn before call to handle response
		if (chassisHttpClientFuture instanceof HttpSyncIncompleteFuture) { // h1 and h2 sync
			if (logger.isDebugEnabled())
				logger.info("Complete sync listener {} on channel {} for response with refCnt {}", chassisHttpClientFuture, cid, hctx.getInMsg().refCnt());
			chassisHttpClientFuture.getFuture().complete(hctx);
		} else if (chassisHttpClientFuture instanceof HttpAsyncIncompleteFuture) { // h1 async // h2 async
			//			logger.info("ascyn cplt {}", completeCnt.incrementAndGet());
			HttpAsyncIncompleteFuture<HttpClientReceiveContext> asyncIncompleteFuture = (HttpAsyncIncompleteFuture<HttpClientReceiveContext>) chassisHttpClientFuture;
			ChassisThreadManager.hcHandlerPool.submit(() -> {
				try {
					asyncIncompleteFuture.callback.accept(hctx, asyncIncompleteFuture.getSentTime());
					// retained above so can enqueue, but netty wont care release all, need manual release all
					if(hctx.isHttp1()) hctx.getInMsg().release();
				} catch (Throwable ex) {
					if (logger.isInfoEnabled()) logger.error(ex, ex);
				}
			});
			if (logger.isDebugEnabled())
				logger.debug("Seeded {} listener complete {} on channel {} for {} with refCnt {}",
					hctx.isHttp1() ? "H1async" : (hctx.isHttp2() ? "H2async" : "H?async"),
					chassisHttpClientFuture.getClass().getSimpleName(),
					cid, hctx.getInMsg().refCnt(),
					hctx.isHttp2() ? "response" : ("for stream " + hctx.streamId)
				);
		} else {
			if (logger.isDebugEnabled())
				logger.debug("[No handler or handler rejected. Context completed. Ignore response]{conn={}, response={}}", monitorID, httpResponse);
		}
	}

	@Override
	protected HttpClientInitializer newClientInitializer(MsgCounter msgCounter) {
		return new HttpClientInitializer(this, msgCounter);
	}

	@Override
	protected void newChannelReady() {
		if (config.sendTimeoutMs > 0)
			RequestTimeoutMonitor.registerService(config.id, this, getHandler()); // registerService timeout sau khi da set counter, set nettyChannel!=nul
	}


	private void asyncSend(HttpRequest request) throws Exception {
		String messageId = request.getMessageId();
		try {
			sendByChannel(nettyChannel, request, config.sendTimeoutMs);
		} catch (SendTimeout sendE) {
			getHandler().chassisDropHandle(request, false); // at client there is no ctx
			ack(messageId); // clear promise of this conn for next use
			throw sendE;
		} catch (Throwable internalError) {
			if (isHttp1()) deregister();
			getHandler().chassisDropHandle(request, true); // at client there is no ctx
			ack(messageId); // clear promise of this conn for next use
			throw internalError;
		} // if no error, do not remove promise for
	}

	@Override
	public void deregister() {
		Channel snapshotChannel;
		if ((snapshotChannel = nettyChannel) != null)
			HttpClientChannelAttribute.getPromiseMap(snapshotChannel).values().stream().map(AtomicReference::get).filter(Objects::nonNull)
				.forEach(pairMsgIdFuture -> ack(pairMsgIdFuture.getLeft()));
		super.deregister();
	}

	public boolean isSendSyncable() {
		Map<String, AtomicReference<Pair<String, HttpIncompleteFuture<HttpClientReceiveContext>>>> promiseMap;
		return !config.to3rdParty || !( // send to 3rd P
			(promiseMap = HttpClientChannelAttribute.getPromiseMap(nettyChannel)) != null
				&& promiseMap.values().stream().anyMatch(resultSet -> { // chi can 1 pair future match to3rdPartyMsgId, nghia la channel khong san sang gui toi 3rdParty
				Pair<String, HttpIncompleteFuture<HttpClientReceiveContext>> currentPair;
				return (currentPair = resultSet.get()) != null && Objects.equals(ChassisConst.to3rdPartyMsgId, currentPair.getLeft());
			}));
	}

	@Override
	public HttpResponse sendSync(HttpRequest request, long timeoutMs) throws Exception {
		String messageId = request.getMessageId();
		HttpIncompleteFuture<HttpClientReceiveContext> syncFuture = setFutureSentId(request, false);
		asyncSend(request);
		long start = TimeUtils.nowNano();
		HttpClientReceiveContext hctx;
		try {
			if (logger.isDebugEnabled()) logger.debug("Waiting for Hclient response@{}", syncFuture);
			if ((hctx = syncFuture.getFuture().get(timeoutMs > 0 ? timeoutMs : config.sendTimeoutMs, TimeUnit.MILLISECONDS)) == null) {
				// responseListener is removed -> ignore
				if (logger.isDebugEnabled())
					logger.warn("[responseListener for http conn {} discarded but found response after {}ms]", getMonitorID(), TimeUtils.miliPassed(start));
			}
		} catch (TimeoutException ex) {
			getHandler().chassisTimeoutHandle(messageId);
			if (isHttp1()) deregister();
			throw ex;
		}
		return hctx != null ? hctx.getInMsg() : null; // null mean msg is discarded while waiting response
	}


	@Override
	public void send(HttpRequest request) throws Exception {
		// Http1 only suuport block sending with/without timeout
		// this API equal to send with default timeout
		setFutureSentId(request, true);
		asyncSend(request);
	}

	private HttpIncompleteFuture<HttpClientReceiveContext> setFutureSentId(HttpRequest request, boolean isAsync) {
		String messageId = config.to3rdParty ? ChassisConst.to3rdPartyMsgId : request.getMessageId(); // must mark before send
		HttpClientHandler handler = getHandler();
		HttpIncompleteFuture<HttpClientReceiveContext> future = isAsync
			? new HttpAsyncIncompleteFuture<>(TimeUtils.nowNano(), (hctx, start) -> {
			try {
				if (!TimeUtils.isTimeoutMili(start, config.sendTimeoutMs)) {
					if (hctx != null) handler.chassisHandle(hctx);
					else handler.chassisDropHandle(request, true);
				} else handler.chassisTimeoutHandle(messageId); // response ve cham
			} catch (Exception ex) {
				logger.error(ex, ex);
				handler.chassisTimeoutHandle(messageId);
			}
		})
			: new HttpSyncIncompleteFuture<>(TimeUtils.nowNano());
		Map<String, AtomicReference<Pair<String, HttpIncompleteFuture<HttpClientReceiveContext>>>> promiseMap;
		Channel snapshotChannel;
		while ((snapshotChannel = nettyChannel) == null || (promiseMap = HttpClientChannelAttribute.getPromiseMap(snapshotChannel)) == null)
			TimeUtils.waitSafeTick(); // wait until channel ready
		AtomicReference<Pair<String, HttpIncompleteFuture<HttpClientReceiveContext>>> resultSet =
			promiseMap.computeIfAbsent(newSendIdentifier.apply(snapshotChannel), // sendId after has channel to send
				k -> new AtomicReference<>());
		Pair<String, HttpIncompleteFuture<HttpClientReceiveContext>> currentPair;
		while ((currentPair = resultSet.get()) != null && Objects.equals(messageId, currentPair.getLeft())
			|| !resultSet.compareAndSet(null, Pair.of(messageId, future)))
			TimeUtils.waitSafeTick(); // wait until sendId is set, // if there were a same listener on msgId, then wait for them to complete
		return future;
	}

	@Override
	public boolean checkAvailability() {
		return getSentCC(false) < 1; // sync send only
	}

	@Override
	public void onChannelRenew(Channel newChannel) {
		setClientChannel(newChannel);
		setEnable(true);
		if (config.sendTimeoutMs > 0) RequestTimeoutMonitor
			.registerService(config.id, this, getHandler());
		super.onChannelRenew(newChannel);
	}

	@Override
	public boolean ping(ByteBufAllocator allocator) { // not implement, and should not be call
		if (!config.http1.keepAlive) throw new UnsupportedOperationException();
		else { // if (config.isSupport(ChassisConst.SupportVersion.HTTP_2_0)) {
			Channel snapshotChannel = nettyChannel;
			InboundHttp2ToHttpAdapter inboundAdapter = HttpClientChannelAttribute.getInboundAdapter(snapshotChannel);
			try {
				if (isConnected())
					snapshotChannel.writeAndFlush(allocator.buffer(FRAME_HEADER_LENGTH + PING_FRAME_PAYLOAD_LENGTH)
						.writeMedium(PING_FRAME_PAYLOAD_LENGTH)
						.writeByte(PING)
						.writeByte(flags.value())
						.writeInt(0)
						.writeBytes(pingPayload)
					);
				TimeUtils.waitUntil(() -> !TimeUtils.isTimeoutMili(inboundAdapter.lastPing(), h2config.pingTimeoutMs), h2config.pingTimeoutMs);
				if (logger.isDebugEnabled())
					logger.debug("[Receive HTTP/2 ping response]{channel={}, lastPingNs={}}", snapshotChannel, inboundAdapter.lastPing());
				return true;
			} catch (Exception ex) {
				logger.error(ex, ex);
				if (logger.isDebugEnabled())
					logger.debug("[Fail HTTP/2 ping response]{channel={}, lastPingNs={}}", snapshotChannel, inboundAdapter.lastPing());
				return false;
			}
		}
	}

	public void checkOverCreateStreamId() {
		// TODO important!!! need test this
		Channel snapshotChannel = nettyChannel;
		int currentStreamID = HttpChannelAttribute.getCurrentStreamID(snapshotChannel, false);
		if (currentStreamID >= Http2ChannelConfigure.resetStreamIdThreshold
			&& isConnected()) {
			logger.info("Channel {} reach reset streamID threshold. Current:{} |Threshold: {}. Renew", snapshotChannel, currentStreamID, Http2ChannelConfigure.resetStreamIdThreshold);
			int lastReceiveStreamID = CtxHolder.getMaxReceiveStreamID(snapshotChannel);
			long startGraceful = TimeUtils.nowNano();
			logger.info((
					TimeUtils.completeUnder(() -> lastReceiveStreamID >= HttpChannelAttribute.getCurrentStreamID(snapshotChannel, false), config.sendTimeoutMs * 2)
						? "[Receive all response from  HTTP2 channel. Gracefully close]"
						: "[Timeout waiting response HTTP2 channel. Force close]")
					+ "{channel={}, timeMs={}, currentStreamID={}, lastReceiveStreamID={}",
				snapshotChannel, TimeUtils.miliPassed(startGraceful), HttpChannelAttribute.getCurrentStreamID(snapshotChannel, false), lastReceiveStreamID);
			deregister();
		}
	}

	Function<Channel, String> newSendIdentifier = c -> {
		while (CtxHolder.isHttp2(c) && !claimingStreamId.compareAndSet(false, true))
			TimeUtils.waitSafeTick(); // wait until allow next claim
		return CtxHolder.getChannelShort(c) + (CtxHolder.isHttp2(c) ? String.format("_%d", HttpChannelAttribute.getCurrentStreamID(c, false) + 2) : "");
	};
	static BiFunction<Channel, Integer, String> getReceiveIdentifier = (c, streamId) ->
		CtxHolder.getChannelShort(c) + (CtxHolder.isHttp2(c) ? String.format("_%d", streamId) : "");
	static BiFunction<Channel, Integer, AtomicReference<Pair<String/*msgId*/, HttpIncompleteFuture<HttpClientReceiveContext>>>> getSentMsgId = (c, streamId) ->
		c == null ? null : HttpClientChannelAttribute.getPromiseMap(c).get(getReceiveIdentifier.apply(c, streamId));

	@Override
	public synchronized void close() {
		super.close(); // eliminate from management components
		try {
			Channel snapshotChannel = nettyChannel;
			if (snapshotChannel != null) {
				RequestTimeoutMonitor.unregister(snapshotChannel);
				snapshotChannel.close();
			}
			logger.debug("[HTTP connection closed]{id={}, endpoint={}}", config.id, endpointRepresent);
		} catch (Exception ex) {
			logger.error(ex, ex);
		}
	}

	public void ack(String msgId) {
		/**
		 * common knowledge: there is no feature or flag or method that could echo back client the field it sent to server by any implemented server on the market
		 * => any single channel can only fire and wait for response synchronous (blocked way)
		 *
		 * watched in {@link #sendByChannel(Channel, HttpRequest, GenericFutureListener[])} by messageId, client always has messageId
		 * 	for H1 map messageId to channelId
		 * 	for http2 multi msg channel, by multi stream, map messageId to streamId
		 * 		if map is global, map messageId to channelId_streamId
		 * currently, syncPromiseMap is added into channel attr -> channel specific
		 * => map of h1 channel only has 1 key is its channelId
		 */

		if (msgId != null) {
			PeerCounter counter = getCounter();
			if (counter != null) {
				counter.ack(msgId, true);
				counter.recordReceive();
			}
			usable.set(true);
		}
	}

	public boolean isGoAway() {
		return HttpClientChannelAttribute.getInboundAdapter(nettyChannel).isGoAway();
	}

	public void sendGoAway(long errorCode) {
		sendGoAway(new DefaultHttp2GoAwayFrame(errorCode));
	}

	public void sendGoAway(long errorCode, ByteBuf debugData) {
		sendGoAway(new DefaultHttp2GoAwayFrame(errorCode, debugData));
	}

	public void sendGoAway(Http2Error e) {
		sendGoAway(new DefaultHttp2GoAwayFrame(e));
	}

	public void sendGoAway(Http2Error e, ByteBuf debugData) {
		sendGoAway(new DefaultHttp2GoAwayFrame(e, debugData));
	}


	private void sendGoAway(Http2GoAwayFrame msg) {
		if (isConnected()) nettyChannel.writeAndFlush(msg).addListener(future -> {
			if (logger.isDebugEnabled())
				logger.error(future.isSuccess() ? "GOAWAY frame sent successfully." : "Failed to send GOAWAY frame.");
			future.cause().printStackTrace();
		});
	}
}
