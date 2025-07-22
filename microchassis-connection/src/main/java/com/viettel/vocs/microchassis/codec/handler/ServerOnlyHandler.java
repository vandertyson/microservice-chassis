package com.viettel.vocs.microchassis.codec.handler;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.context.ChassisReplyContext;
import com.viettel.vocs.microchassis.codec.context.ServerContext;
import com.viettel.vocs.microchassis.codec.handler.tcp.ServerHandler;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * here is class for child class only need to define handler it used
 * everything here do nothing, no need to define UserHandler APIs
 * <p>
 * APPs NEED TO SELF CALL DECOMPOSE() on MSG and CTX if not call ctx.send(...)
 *
 * @param <Req>
 * @param <Res>
 * @param <CtxResponse>
 */
public abstract class ServerOnlyHandler<Req extends ChassisMgrableMsg, Res extends ChassisMgrableMsg, CtxResponse extends ChassisReplyContext<Req, Res> & ServerContext<Req, Res>>
	implements ServerHandler<Req, Res, CtxResponse> {
	protected static final Logger logger = LogManager.getLogger(ServerOnlyHandler.class);
	protected final PeerConfig config;

	protected ServerOnlyHandler(PeerConfig config) {
		this.config = config;
	}

	@Override
	public void dropHandle(Req failedReq, boolean isSent) {
		if (logger.isDebugEnabled())
			logger.debug("Server dropped message by default msg={}", failedReq);
	}

	@Override
	public void handle(CtxResponse ctxResponse) {
		if (logger.isDebugEnabled())
			logger.debug("Server handle message by default msg={}", ctxResponse.getInMsg());
	}

	//	@Override
//	public void timeoutHandle(String requestID) { // no need for server
//		if(logger.isDebugEnabled()) logger.debug("Server timeout receiving requestID={}", requestID);
//	}

	@Override
	public final void chassisHandle(CtxResponse ctx) {
		if (logger.isDebugEnabled()) logger.debug("chassisHandle ctx called. serverContext={}", ctx);
		if (ctx != null) {
			if (!ctx.isInMgr() && ctx.getInMsg().verifyCheckSum()) handle(ctx); // handle all included MgrMsg, // release at channelRead0
			if (config.decomposeAfterHandle) { // other servers proto or client, decompose by config
				if (logger.isDebugEnabled()) logger.info("Auto decompose on chassisHandle");
				ctx.decompose(); // release(1) for http2, if no it leak // Http1 only need release 1
			}
		}
	}

	public final void chassisServerDropHandle(CtxResponse ctx, boolean isSent) {
		dropHandle(ctx.getInMsg(), isSent); // release at channelRead0
		serverDropHandle(ctx, isSent);
	}

	public void serverDropHandle(CtxResponse ctx, boolean isSent) {
		// do nothing
	}

	;

	@Override
	public final void chassisDropHandle(Req failedReq, boolean isSent) {
		dropHandle(failedReq, isSent); // release at channelRead0
	}
//	@Override @Deprecated // no need for server
//	public final void chassisTimeoutHandle(String msgId){
//		timeoutHandle(msgId);
//	};
}
