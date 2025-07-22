package com.viettel.vocs.microchassis.tcp.client;

import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.config.ClientConfiguration;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;
import com.viettel.vocs.microchassis.tcp.codec.Msg;

public class TcpInstanceClient extends TcpBasedInstanceClient<Msg, ClientConfiguration, TcpHandler> {
	public static TcpInstanceClient createInstance(String clientId, Handler<TcpContext> asyncSuccessHandler, Handler<String> asyncFailHandler) {
		ClientConfiguration instance1Conf = new ClientConfiguration(clientId);
		return new TcpInstanceClient(instance1Conf, new TcpHandler(instance1Conf) {
			private final Handler<TcpContext> sHandler = asyncSuccessHandler;
			private final Handler<String> fHandler = asyncFailHandler;

			@Override
			public void handle(TcpContext ctx) {
				if (sHandler != null) sHandler.handle(ctx);
				checkAndCloseConn(ctx.getChannel());
			}

			@Override
			public void dropHandle(Msg failedReq, boolean isSent) {
				if (fHandler != null) fHandler.handle(failedReq.getMessageId());
			}

			@Override
			public void timeoutHandle(String requestID) {
				if (fHandler != null) fHandler.handle(requestID);
			}
		});
	}
	public TcpInstanceClient(ClientConfiguration instanceConfig, TcpHandler handler) {
		super(instanceConfig, handler);
	}
	@Override
	public Msg createReq(byte[] data, String path, String msgId) {
		Msg msg = new Msg(bytebufAllocator, path, msgId);
		msg.writeFrom(data);
		return msg;
	}


	public Msg createReq(byte[] data, String path) {
		return createReq(data, path, Msg.newMsgId());
	}

	@Override
	public final TcpClientConnection createConnection(Site<ClientConfiguration> ownerSite, Endpoint endpoint, EventHandler eventHandler, boolean monitor) {
		return createConnection(ownerSite, endpoint, handler, eventHandler, monitor);
	}

	@Override
	public final TcpClientConnection createConnection(Site<ClientConfiguration> ownerSite, Endpoint endpoint, TcpHandler handler, EventHandler eventHandler, boolean monitor) {
		TcpClientConnection conn = new TcpClientConnection(config, eventHandler, ownerSite, endpoint, handler, bootstrap, ownerSite.getRouteStrategyRef());
		return !conn.isClosed() ? conn: null; // not to be closed after register // => allow to add new connection
	}
}
