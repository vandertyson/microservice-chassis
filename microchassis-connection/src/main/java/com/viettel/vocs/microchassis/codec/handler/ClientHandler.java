package com.viettel.vocs.microchassis.codec.handler;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.context.ClientReceiveContext;
import com.viettel.vocs.microchassis.codec.context.CtxHolder;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * here is class for child class only need to define handler it used
 * everything here do nothing
 *
 * @param <Req>
 * @param <Res>
 * @param <ReceiveCtx>
 */
public abstract class ClientHandler<Req extends ChassisMgrableMsg, Res extends ChassisMgrableMsg, ReceiveCtx extends ClientReceiveContext<Res>>
	implements ClientReceiveHandler<Res, Req, ReceiveCtx>, ClientSendHandler<Req, Res, ReceiveCtx>{
	private static final Logger logger = LogManager.getLogger(ClientHandler.class);
	protected final String clientId;
	protected final PeerConfig config;
	protected ClientHandler(PeerConfig config){
		this.config = config;
		this.clientId = this.config.id;
	}
	@Override
	public void dropHandle(Req failedReq, boolean isSent) {
		if(logger.isDebugEnabled()) logger.debug("[Client:{}] dropped message by default msg={}, sent={}", clientId,  failedReq, isSent);
	}

	@Override
	public void timeoutHandle(String requestID) {
		if (logger.isDebugEnabled()) logger.debug("[Client:{}] timeout sending msgId={}", clientId, requestID);
	}
	@Override
	public void handle(ReceiveCtx receiveCtx) {
		if(logger.isDebugEnabled()) logger.debug("[Client:{}] Client handled message msg={}", clientId, receiveCtx.getInMsg());
	}
	// day la mgrFirstHandler wrap cho userHandler, de xu ly goi tin mgr truoc, neu khong phai thi goi den userHandler, can phai co vi handler nay se pass vao trong msgDecoder de xu ly msg
	@Override
	public final void chassisHandle(ReceiveCtx ctx) {
		if (logger.isDebugEnabled()) logger.debug("chassisHandle ctx called. tcpContext={}", ctx);
		if (ctx!=null) {
			if (!ctx.isInMgr()) handle(ctx); // handle all included MgrMsg
			if(!(CtxHolder.isHttp1(ctx.getChannel())) // http1 sendsync, so do not decompose
				&& config.decomposeAfterHandle) {
				if(logger.isDebugEnabled()) logger.info("Auto decompose on chassisHandle");
				ctx.decompose();
			}
		}
	}

	@Override
	public final void chassisDropHandle(Req failedMsg, boolean isSent) {
		/**
		 * mgr se co body empty hoac 8bytes, nen cac thao tac tren body du lieu do nguoi dung dinh nghia
		 * trong HandlerWithContext co the gay ra null pointer bytebuf idx, do do tat ca cac cho goi handler.xxx( ) deu se phai loc rieng luong cho mgrHandler
		 * 	ping khong can retry, co check channel.isActive roi
		 * 	serverDrop drop luon, request sau se lai thay serverDrop, khong can retry
		 * 	create khong can retry, co Tocheck interval roi
		 */
		if (logger.isDebugEnabled()) logger.debug("chassisSelfDropHandle ctx called. tcpContext={}", failedMsg);
		if(failedMsg!=null) {
			if (!failedMsg.isMgr()) dropHandle(failedMsg, isSent); // at client there is no ctx
			failedMsg.decompose();
		}
	}

	@Override
	public final void chassisTimeoutHandle(String requestID) {
		if (logger.isDebugEnabled()) logger.debug("chassisTimeoutHandle ctx called for ReqId={}", requestID);
		timeoutHandle(requestID);
	}
}
