package com.viettel.vocs.microchassis.serviceRegistry.codec.handler;

import com.viettel.vocs.microchassis.codec.handler.tcp.TcpBasedHandler;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.LBRAdapter;
import com.viettel.vocs.microchassis.serviceRegistry.codec.LBRMsg;
import com.viettel.vocs.microchassis.serviceRegistry.codec.context.LbrContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

public class LbrHandler extends TcpBasedHandler<LBRMsg, LbrContext> {
	private static final Logger logger = LogManager.getLogger(LbrHandler.class);
	private final AtomicInteger peerDropCounter;
	private final LBRAdapter lbrAdapter;
	// TODO define timeout flow // TODO propagate update fail at config.maxUpdateFail -> remove client from service for it to register again later
	public LbrHandler(PeerConfig config, AtomicInteger peerDropCounter, LBRAdapter adapter) {
		super(config);
		this.peerDropCounter = peerDropCounter;
		lbrAdapter = adapter;
	}

	@Override
	public void handle(LbrContext ctx) {
		if (ctx != null && ctx.validateCtx()) {
			ctx.getInMsg().headers().set(LBRMsg.STATE, "update");
			lbrAdapter.applyUpdate(ctx.getInMsg());
		}
	}

	@Override
	public void dropHandle(LBRMsg dropMsg, boolean isSent) {
		logger.info("Center LBR dropped sync {} time", peerDropCounter.incrementAndGet());
	}
}
