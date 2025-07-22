package com.viettel.autotest.microchassis.benchmark;

import com.viettel.autotest.microchassis.connection.config.*;
import com.viettel.autotest.microchassis.lib.generator.Http1PayloadGenerator;
import com.viettel.autotest.microchassis.lib.generator.PayloadGenerator;
import com.viettel.autotest.microchassis.lib.generator.TcpPayloadGenerator;
import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.MathUtils;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.queue.IIQ;
import com.viettel.vocs.common.queue.WaitItem;
import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.MsgType;
import com.viettel.vocs.microchassis.codec.context.ChassisNoReplyNoContext;
import com.viettel.vocs.microchassis.codec.context.ChassisReplyContext;
import com.viettel.vocs.microchassis.codec.context.ServerContext;
import com.viettel.vocs.microchassis.codec.context.http.HttpClientReceiveContext;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.codec.handler.http.HttpAPI;
import com.viettel.vocs.microchassis.codec.handler.http.HttpClientHandler;
import com.viettel.vocs.microchassis.codec.handler.http.HttpServerHandler;
import com.viettel.vocs.microchassis.codec.handler.tcp.ServerHandler;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.client.NettyClient;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.connection.exception.SendTimeout;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.depricated.ServerHandlers;
import com.viettel.vocs.microchassis.http.client.HttpClient;
import com.viettel.vocs.microchassis.http.codec.HttpRequest;
import com.viettel.vocs.microchassis.http.codec.HttpResponse;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import com.viettel.vocs.microchassis.tcp.client.TcpClient;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import com.viettel.vocs.microchassis.tcp.codec.MsgHeader;
import com.viettel.vocs.microchassis.tcp.server.TcpServer;
import com.viettel.vocs.microchassis.util.MonitorManager;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.viettel.vocs.microchassis.base.ChassisConst.CustomHeader.inRqHeader;

interface AsyncCallback<Msg extends ChassisMgrableMsg> {
	void onSuccess(Msg msg);

	void onFail();
}
class HttpServerGenerator extends HttpServer {
	final Http1PayloadGenerator fake1TPSGenerator;
	ExecutorService fakeServerHandlerPool = Executors.newFixedThreadPool(
		config.channelConfiguration.numWorkerThread, new BasicThreadFactory.Builder()
			.namingPattern("FHW-%d")
			.daemon(true)
			.priority(Thread.MAX_PRIORITY)
			.build());

	public HttpServerGenerator(HttpGeneratorServerConfiguration options) throws Exception {
		super(options);
		fake1TPSGenerator = new Http1PayloadGenerator(options, getBytebufAllocator());// autorun
	}

	@Override
	public HttpServerGenerator start(HttpServerHandler handler) {
		// generate load instead of listen server
		for (int i = 0; i < config.channelConfiguration.numWorkerThread; i++) {
			fakeServerHandlerPool.execute(new WorkerThread<>(fake1TPSGenerator.queueMgr, getBytebufAllocator(), (Microservice.BusinessHttp1ServerHandler) handler));
		}
		return this;
	}
}

class ServerGenerator extends TcpServer {
	final TcpPayloadGenerator fakeTPSGenerator;
	ExecutorService fakeServerHandlerPool = Executors.newFixedThreadPool(
		config.channelConfiguration.numWorkerThread, new BasicThreadFactory.Builder()
			.namingPattern("FTW-%d")
			.daemon(true)
			.priority(Thread.MAX_PRIORITY)
			.build());

	public ServerGenerator(GeneratorServerConfiguration options) throws Exception {
		super(options);
		fakeTPSGenerator = new TcpPayloadGenerator(options, getBytebufAllocator());// autorun
	}

	@Override
	public ServerGenerator start(TcpHandler handlers) {
		// generate load instead of listen server
		for (int i = 0; i < config.channelConfiguration.numWorkerThread; i++) {
			fakeServerHandlerPool.execute(new WorkerThread<>(fakeTPSGenerator.queueMgr, getBytebufAllocator(), (Microservice.BusinessTcpHandler) handlers));
		}
		return this;
	}
}

public interface Microservice {
	Logger logger = LogManager.getLogger(Microservice.class);
	MonitorManager mon = MonitorManager.getInstance();
	Map<String, AsyncCallback<HttpResponse>> responseMap1 = new ConcurrentHashMap<>();
	Map<String, AsyncCallback<Msg>> responseMapTcp = new ConcurrentHashMap<>();


	static void timeoutH(String requestID) {
		responseMap1.remove(requestID);
	}

	static void timeoutTcp(String requestID) {
		mon.timeout();
		AsyncCallback<?> remove = responseMapTcp.remove(requestID);
		if (remove != null) remove.onFail();
	}

	// cac server start tren main thread, Netty se mo BossThread cho cac server, main thread co the ket thuc, main chi dung de monitor
	// cac worker thread trong poll execute
	interface Constants {
		String httpServerId = "hServer";
		String tcpServerId = "tcpServer";
		String tcpClientId = "tcpClient";
		String hClientId = "hclient";
	}

	ConnectionManager manager = ConnectionManager.getInstance();

	List<TcpClient> tcpClients = new ArrayList<>();
	List<HttpClient> hClients = new ArrayList<>();


	static HttpClient getHClient() {
		return hClients.get(MathUtils.randomNextInt(hClients.size()));
	}

	static TcpClient getTcpClient() {
		return tcpClients.get(MathUtils.randomNextInt(tcpClients.size()));
	}

	private static void waitAllClients() throws TimeoutException {
		logger.info("Wait all clients stable");
		TimeUtils.waitUntil(() ->
			tcpClients.parallelStream().allMatch(client -> client.checkAndWaitStableAllSite(1_000_000_000L))
				&& hClients.parallelStream().allMatch(client -> client.checkAndWaitStableAllSite(1_000_000_000L)), 30_000L);
		logger.info("All clients stabled");
	}

	interface BusinessHandler<Client extends NettyClient, Req extends ChassisMgrableMsg, Res extends ChassisMgrableMsg, Ctx extends ChassisReplyContext<Req,Res>>
		extends ServerHandler<Req, Res, Ctx>{

		static int ttl(HttpServerContext ctx) {
			String ttl = ctx.getInMsg().getStringHeaders().get("ttl");
			return ttl == null ? 0 : Integer.parseInt(ttl);
		}

		AutoTestClientConfig getConfig();

		default boolean isGenerator() {
			return getConfig() instanceof GeneratorConfig;
		}

		boolean isEcho();

		Client selectClient();

		void timeout(String msgId);

		default Res transformSync(Req req, ByteBufAllocator serverAlloc) {
			if (isEcho()) return buildEchoMsg(req, serverAlloc); // db go here only, not async
			else {
				// only for direct(passive) call from server transformer, not for auto
				try {
					return (Res) selectClient().sendSync(buildForwardMsg(req, serverAlloc));
				} catch (SendTimeout ex) {
					logger.error(ex);
					timeout(req.getMessageId());
					return null;
				} catch (Exception e) {
					logger.error(CommonConfig.InstanceInfo.VDU_NAME + "/" + BenchmarkClientConfiguration.clientMode, e);
					mon.clientSendDrop();
					return null;
				}
			}
		}


		default void transformAsync(ChassisNoReplyNoContext<Req> tctx, ByteBufAllocator serverAlloc, AsyncCallback<Res> cb) {
			if (!isEcho()) {
				Req req = buildForwardMsg(tctx.getInMsg(), serverAlloc);
				req.setUrl(PayloadGenerator.testUrl);
				String messageId = req.getMessageId();
				getResponseMap().put(messageId, cb);
				try {
					selectClient().send(req);
				} catch (SendTimeout | TimeoutException ex) {
					logger.debug(ex);
					timeout(messageId);
				} catch (Exception e) {
					logger.error(CommonConfig.InstanceInfo.VDU_NAME + "/" + BenchmarkClientConfiguration.clientMode, e);
					e.printStackTrace();
					mon.clientSendDrop();
				}
			} // else // do nothing on transform
		}

		Map<String, AsyncCallback<Res>> getResponseMap();

		Req buildForwardMsg(Req incomingMsg, ByteBufAllocator serverAlloc);

		Res buildEchoMsg(Req req, ByteBufAllocator serverAlloc);

		default void processCtx(ChassisNoReplyNoContext<Req> ctx, ByteBufAllocator serverAlloc) {
			Client client = selectClient();
			try {
				if (client == null || !client.isConnected()) replyDrop(ctx);
				else if (getConfig().isSendSync())
					reply(ctx, transformSync(ctx.getInMsg(), serverAlloc));
				else transformAsync(ctx, serverAlloc, new AsyncCallback<>() {
						@Override
						public void onSuccess(Res afterTransformFromClientMsg) {
							// transform se call client.sendAsync va luu responseMap callback nay
							// response async qua client TcpHandler.handle() se lay callback nay ra khoi responseMap, xu ly msg client tra ve
							// 	 viec xu ly nay tao 1 future thuc thi boi SharedThreadPool voi so worker co dinh, giong mo hinh cua server, con callback nay se ket thuc de reclaim netty client thread phuc vu response khac

							reply(ctx, afterTransformFromClientMsg);
						}

						@Override
						public void onFail() {
							replyDrop(ctx);  // null neu la gateway, khong co ctx de return, // da duoc xu ly o ServerTcpBasedDecoder
						}
					});
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}

		default void replyDrop(ChassisNoReplyNoContext<Req> ctr) {
			if (ctr instanceof TcpContext) {
				TcpContext ctx = (TcpContext) ctr;
				if (ctx.validateCtx())
					ctx.sendDrop(f -> mon.serverNotifyDrop()); // -> client -> BiMsgDecoder -> handleAsync -> sentDropHandler
			} else if (ctr instanceof HttpServerContext) {
				HttpServerContext ctx = (HttpServerContext) ctr;
				if (ctx.validateCtx())
					ctx.sendDrop(f -> mon.serverNotifyDrop()); // -> client -> BiMsgDecoder -> handleAsync -> sentDropHandler
			}
			//					mon.serverFlush(start); // da duoc thong ke bang peerdrop, drop va timeout roi
		}

		static void updateReplyHeader(HttpHeaders reqHeaders, HttpHeaders resHeaders, long sendCyDuration, long decodeTime) {
			long thisPodTotalQueueTime = Long.parseLong(reqHeaders.get(ChassisConst.CustomHeader.inSqHeader)) + Long.parseLong(resHeaders.get(inRqHeader));
			resHeaders.set("queueTime", String.valueOf( // accummulate InSQ InRQ Time
				Long.parseLong(resHeaders.get("queueTime")) + thisPodTotalQueueTime));
			resHeaders.set("processTime", String.valueOf( // effective time of microchassis
				Long.parseLong(resHeaders.get("processTime"))  // accummulate with lower layer process time
					+ sendCyDuration  // SendCy khong bao gom InSQ
					- Long.parseLong(resHeaders.get("totalFlush"))
					- Long.parseLong(resHeaders.get(inRqHeader))));
			mon.sendRoundtrip(decodeTime, BenchmarkClientConfiguration.clientModeCode, Long.parseLong(resHeaders.get("queueTime")) + Long.parseLong(resHeaders.get("processTime")) / 1000/*to micro*/);
		}

		static void updateReplyHeader(MsgHeader reqHeaders, MsgHeader resHeaders, long sendCyDuration, long decodeTime) {

			long thisPodTotalQueueTime = (long) reqHeaders.get(ChassisConst.CustomHeader.inSqHeader) + (long) resHeaders.get(inRqHeader);
			resHeaders.set("queueTime", String.valueOf( // accummulate InSQ InRQ Time
				Long.parseLong(resHeaders.getString("queueTime")) + thisPodTotalQueueTime));
			resHeaders.set("processTime", String.valueOf( // effective time of microchassis
				Long.parseLong(resHeaders.getString("processTime"))  // accummulate with lower layer process time
					+ sendCyDuration  // SendCy khong bao gom InSQ
					- Long.parseLong(resHeaders.getString("totalFlush"))
					- (long) resHeaders.get(inRqHeader)));
			mon.sendRoundtrip(decodeTime, BenchmarkClientConfiguration.clientModeCode, Long.parseLong(resHeaders.getString("queueTime")) + Long.parseLong(resHeaders.getString("processTime")) / 1000/*to micro*/);
		}

		default void sendEcho(ChassisNoReplyNoContext<Req> ctx, ByteBufAllocator serverAlloc) {
			long dequeueTime = TimeUtils.nowNano();
			// db go here only, echo without using handleThreads
			if (isGenerator()) {
				mon.sendRoundtrip(dequeueTime, BenchmarkClientConfiguration.clientModeCode, ctx.getInMsg().readableBytes());
			} else {
				if (ctx instanceof ServerContext) {
					ServerContext sctx = (ServerContext) ctx;
					Res res = buildEchoMsg(ctx.getInMsg(), serverAlloc);
					String dequeueTillNow = String.valueOf(dequeueTime - ctx.getInMsg().getDecodeTime());
					if (ctx instanceof TcpContext) {
						TcpContext tctx = (TcpContext) ctx;
						tctx.getCoMsg().headers().set("queueTime", dequeueTillNow);
					} else if (ctx instanceof HttpServerContext) {
						HttpServerContext h1ctx = (HttpServerContext) ctx;
						h1ctx.getCoMsg().headers().set("queueTime", dequeueTillNow);
					}
					mon.sendRoundtrip(dequeueTime, BenchmarkClientConfiguration.clientModeCode, ctx.getInMsg().readableBytes());
					sctx.send(res, null);
				} else
					mon.sendRoundtrip(dequeueTime, BenchmarkClientConfiguration.clientModeCode, ctx.getInMsg().readableBytes());
			}
		}

		default void reply(ChassisNoReplyNoContext<Req> ctx, Res res) {
			if (res != null  // voi sync, msg null ~> peerDrop
				&& !MsgType.DROP.getUrl().equals(res.getUrl())) {
				Req inMsg = ctx.getInMsg();
				long decodeTime = inMsg.getDecodeTime();
				long sendCyDuration = TimeUtils.nowNano() - decodeTime;
				mon.wardforTime(res.getDecodeTime());
				Iterable reqHeaders = inMsg.headers();
				Iterable resHeaders = res.headers();
				if (inMsg instanceof HttpRequest)
					updateReplyHeader((HttpHeaders) reqHeaders, (HttpHeaders) resHeaders, sendCyDuration, decodeTime);
				else if (inMsg instanceof Msg)
					updateReplyHeader((MsgHeader) reqHeaders, (MsgHeader) resHeaders, sendCyDuration, decodeTime);
				ServerContext ctr;
				if (ctx instanceof ServerContext && (ctr = (ServerContext) ctx).validateCtx()) {  // null neu la gateway, khong co ctx de return
					try {
						ctr.dumpToCo(res);
						ctr.newChannelPromiseThenResolve(res);
						ctr.send();
					} catch (Exception e) {
						logger.error("Reply error", e);
					}
				}
			}  // handled at ServerTcpBasedDrop
		}
	}

	class BusinessTcpHandler extends TcpHandler implements BusinessHandler<TcpClient, Msg, Msg, TcpContext> {
		@Override
		public void timeout(String msgId) {
			timeoutTcp(msgId);
		}

		@Override
		public Map<String, AsyncCallback<Msg>> getResponseMap() {
			return responseMapTcp;
		}

		@Override
		public TcpClient selectClient() {
			return getTcpClient();
		}

		@Override
		public Msg buildForwardMsg(Msg req, ByteBufAllocator serverAlloc) {
			return req.replicate();
		}

		@Override
		public Msg buildEchoMsg(Msg req, ByteBufAllocator serverAlloc) {
			return req.replicate();
		}

		public ClientConfiguration getConfig() {
			return (ClientConfiguration) config;
		}

		public BusinessTcpHandler(PeerConfig config) {
			super(config);
		}


		public boolean isEcho() {
			return config instanceof EchoServerConfiguration;
		}
	}

	class BusinessHttp1ServerHandler extends HttpServerHandler implements BusinessHandler<HttpClient, HttpRequest, HttpResponse, HttpServerContext> {
		@Override
		public void timeout(String msgId) {
			timeoutH(msgId);
		}

		public com.viettel.autotest.microchassis.connection.config.HttpClientOptions getConfig() {
			return (com.viettel.autotest.microchassis.connection.config.HttpClientOptions) config;
		}

		@Override
		public HttpResponse buildEchoMsg(HttpRequest req, ByteBufAllocator serverAlloc) {
			HttpResponse response = new HttpResponse(serverAlloc, req.getMessageId());
			response.writeFrom(req.getContent());
			return response;
		}
		@Override
		public HttpRequest buildForwardMsg(HttpRequest req, ByteBufAllocator serverAlloc) {
			HttpRequest defaultFullHttpRequest = req.replicate();
			Map<String, String> headers = req.getStringHeaders();
			if (headers.containsKey("ttl"))
				defaultFullHttpRequest.headers().set("ttl", String.valueOf(Integer.parseInt(headers.get("ttl")) - 1));
			if (headers.containsKey("last-stream-id-increment"))
				defaultFullHttpRequest.headers().set("last-stream-id-increment", headers.get("last-stream-id-increment"));
			return defaultFullHttpRequest;
		}

		@Override
		public HttpClient selectClient() {
			return getHClient();
		}

		@Override
		public Map<String, AsyncCallback<HttpResponse>> getResponseMap() {
			return responseMap1;
		}

		public boolean isEcho() {
			return config instanceof EchoHttpServerConfiguration;
		}
		public BusinessHttp1ServerHandler(HttpServerOptions config) {
			super(config);
			registerApiExact("/shutdown", new HttpAPI() {
				@Override
				public void handle(HttpServerContext ctx) {
					if (BusinessHandler.ttl(ctx) == 0) { // shutdown from tail service chain
						logger.info("starting shutdown");
						String lastStreamIdIncrement = ctx.getInMsg().getStringHeaders().get("last-stream-id-increment");
						((HttpServer) ConnectionManager.getInstance().getServer(Constants.httpServerId))
							.notifyStopIncremental(lastStreamIdIncrement == null ? 0 : Integer.parseInt(lastStreamIdIncrement));
						ctx.send();
						logger.info("graceful shutdown complete");
					}
				}
			});
			registerApiExact("/forward", new HttpAPI() {
				@Override
				public void handle(HttpServerContext ctx) {
					if (BusinessHandler.ttl(ctx) == 0) {
						sendEcho(ctx, ctx.getCtx().alloc());
					}
				}
			});
		}
	}


	private static void openServers() throws Exception {
		waitAllClients();
		HttpServer hServer = ConnectionManager.getInstance().getServer(Constants.httpServerId);
		if (hServer != null) {
			boolean isGenerator = hServer.getConfig() instanceof HttpGeneratorServerConfiguration;
			(hServer = isGenerator ? new HttpServerGenerator((HttpGeneratorServerConfiguration) hServer.getConfig()) : hServer)
				.start(new BusinessHttp1ServerHandler(hServer.getConfig()));
		}
		TcpServer tcpServer = ConnectionManager.getInstance().getServer(Constants.tcpServerId);
		if (tcpServer != null) {
			if (tcpServer.getConfig() instanceof GeneratorServerConfiguration)
				tcpServer = new ServerGenerator((GeneratorServerConfiguration) tcpServer.getConfig());
			tcpServer.start(new BusinessTcpHandler(tcpServer.getConfig()) {
				@Override
				public void handle(TcpContext tctx) {
					if (isEcho()) sendEcho(tctx, tctx.getCtx().alloc());
					processCtx(tctx, tctx.getCtx().alloc());
				}
			});
		}
		if (hServer != null) hServer.waitServerClose();
		if (tcpServer != null) tcpServer.waitServerClose();
		setupShutdownHook(hServer);
	}


	private static void openClients() throws TimeoutException { // start all found clients
		HttpClient h1client;
		TcpClient tcpClient;
		int i = 1;
		while ((h1client = manager.getClient(Constants.hClientId + i)) != null) {
			h1client.start(new HttpClientHandler(h1client.getConfig()) {
				@Override
				public void handle(HttpClientReceiveContext ctx) {
					HttpResponse msg = ctx.getInMsg();
					try {
						AsyncCallback<HttpResponse> cb = responseMap1.remove(msg.getMessageId());
						if (cb != null) {
							msg.retain(); // retain for async handle
							cb.onSuccess(msg);
						} else msg.decompose();
					} catch (Exception ex) {
						logger.error(ex, ex);
						msg.decompose();
					}
				}

				@Override
				public void timeoutHandle(String requestID) {
					timeoutH(requestID);
				}
			});
			hClients.add(h1client);
			i++;
		}
		i = 1;
		while ((tcpClient = manager.getClient(Constants.tcpClientId + i)) != null) {
			tcpClient.start(new TcpHandler(tcpClient.getConfig()) {
				@Override
				public void timeoutHandle(String requestID) {
					timeoutTcp(requestID);
				}

				@Override
				public void handle(TcpContext tctx) {
					AsyncCallback cb = responseMapTcp.remove(tctx.getInID());
					if (cb != null) {
						// do more transform here in scope of client handler transform if needed
						cb.onSuccess(tctx.getInMsg());
					}
				}

				@Override
				public void dropHandle(Msg failedReq, boolean isSent) {
					AsyncCallback<Msg> remove;
					if (isSent) {
						remove = responseMapTcp.remove(failedReq.getMessageId());
						if (remove != null) remove.onFail();
						mon.clientSendDrop();
					} else {
						TcpClient tcpClient = getTcpClient();
						if (tcpClient.isSendable()) {
							try {
								failedReq.setUrl(PayloadGenerator.testUrl);
								String messageId = failedReq.getMessageId();
								try {
									tcpClient.send(failedReq);
								} catch (SendTimeout ex) {
									logger.debug(ex);
									timeoutTcp(messageId);
								}
							} catch (Exception ex) {
								logger.error(ex, ex);
								remove = responseMapTcp.remove(failedReq.getMessageId());
								if (remove != null) remove.onFail();
							}
							mon.clientRetry();
						} else {
							remove = responseMapTcp.remove(failedReq.getMessageId());
							if (remove != null) remove.onFail();
						}
						mon.clientSendDrop();
					}
				}
			});
			i++;
		}
		while (!(
			tcpClients.parallelStream().allMatch(Site::isSendable)
				&& hClients.parallelStream().allMatch(Site::isSendable)
		)) TimeUtils.waitSafeMili(100);
		manager.startMonitorThread();
	}

	private static void setupShutdownHook(HttpServer hServer) {
		logger.info("add graceful shutdown");
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			logger.info("ShutdownHook");
			if (hServer == null) return;
			//wait for queue empty
			long t = TimeUtils.nowNano();
			System.out.println("=================Graceful business==================");
			logger.info("Graceful business");
			hServer.notifyStop2Stage(() -> Microservice.responseMapTcp.size() > 0);

			System.out.println("=====start wait final alarm ====");
			//Integer integer = waitAlarm.get();
			//System.out.println("=====alarm complete ====" + (System.currentTimeMillis()-l)+" ms. code " + integer);

			System.out.println("=================Graceful time " + TimeUtils.miliPassed(t) + " ms==================");
			logger.info("graceful time=" + TimeUtils.miliPassed(t) + " ms");
			LogManager.shutdown();
			System.out.println("ShutdownHook HTTP");
			t = TimeUtils.nowNano();
			hServer.stop();
			System.out.println("graceful HTTP time=" + TimeUtils.miliPassed(t) + " ms");
		}));
	}

	/**
	 * to make echo server set -DserviceType=echo -DVDU_NAME=abm (or no need, default echo)
	 * to make forward service -DserviceType=forward -DVDU_NAME=chp or any name that match etc/<VDU_NAME>
	 * to make generate client -DserviceType=generator -DVDU_NAME=cgw
	 *
	 * @param args
	 * @throws Exception
	 */
	static void main(String[] args) throws Exception {
		CommonConfig.JavaConfig.CONFIG_FOLDER.setDefault(new File("../etc/", CommonConfig.InstanceInfo.VDU_NAME.get()).getPath());
		LogUtils.setupLog4j2();
		manager.loadYmlConfig();
		logger.info("Starting Chassis autotest Microservice");
		openClients();
		logger.info("Run Microservice"); // voi echo client minTps | maxTps = 91,558.882 | 150,015.984, avgTime 1.615Ms
		openServers();
	}


}
class WorkerThread<Client extends NettyClient, Req extends ChassisMgrableMsg, Res extends ChassisMgrableMsg, Ctx extends ChassisReplyContext<Req, Res>, Handler extends Microservice.BusinessHandler<Client, Req, Res, Ctx>> extends Thread {
	protected static final Logger logger = LogManager.getLogger(WorkerThread.class);
	private final IIQ<ChassisNoReplyNoContext<Req>> genQueue;
	public boolean isRunning = true;
	final ServerHandlers handlers;
	private final ByteBufAllocator fakeAllocer;
	public Handler selectHandler() {
		return (Handler) handlers.get(MathUtils.randomNextInt(handlers.size()));
	}
	WorkerThread(IIQ<ChassisNoReplyNoContext<Req>> genQueue, ByteBufAllocator fakeAllocer, Handler... handlers) {
		this.genQueue = genQueue;
		this.fakeAllocer = fakeAllocer;
		AtomicInteger i= new AtomicInteger();
		this.handlers = ServerHandlers.newInstance();
		Arrays.stream(handlers).forEach(h-> this.handlers.put(String.valueOf(i.incrementAndGet()), h));
	}

	private final WaitItem<ChassisNoReplyNoContext<Req>> waitBox = new WaitItem<>(); // moi thread co 1 wait box

	@Override
	public void run() {
		logger.info("[Server] Start faker dequeue worker thread " + getName());
		ChassisNoReplyNoContext<Req> fakeCtx;
		while (isRunning) {
			try {
				if (genQueue.size() > 0) {
					if ((fakeCtx = genQueue.poll(waitBox)) != null) {
						long dequeueTime = TimeUtils.nowNano();
						if (fakeCtx.getInMsg() != null && fakeCtx.getInData() != null) {
//							// benchmark dequeue off Async2Lock avg 20kTPS
//							// SendCy:as|  415374|  20,724.143|        4|    18964|  287.938|   19784|  19,764.236|  17,664.336|  22,788.212|         5.0|      7043.0|     301.986|           0|           0|       0.000|       0.000|    0.000|200-300=235523 |1000-5000=4220 |500-1000=12177 |100-200=61346 |300-500=87999 |0-100=13751 |5000-Max=358|
							Microservice.mon.inServerQueue(fakeCtx.getInMsg(), genQueue.size(), dequeueTime);
							selectHandler().processCtx(fakeCtx, fakeAllocer); // this flow only apply for FakeR
							fakeCtx.decompose();
						}
					} else TimeUtils.waitSafeTick();
				} //  else // fake gen with ChassisNoReplyNoCtx
			} catch (Exception e) {
				logger.error("Dequeue error", e);
			}
		}
	}
}
