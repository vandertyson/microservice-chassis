/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.codec.handler.tcp;

import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author vttek
 */
public class EchoTcpHandler extends LogTcpHandler{
	private static final Logger logger = LogManager.getLogger(EchoTcpHandler.class);
	public EchoTcpHandler(PeerConfig config) {
		super(config); // init with logHandler
	}
	@Override
	public void handle(TcpContext ctx) {
		byte[] content = ctx.getInData();
		super.handle(ctx); // for log
		if(logger.isDebugEnabled()) logger.debug("Echo handler receive content length {}", content.length);
		ctx.send(content, ctx);
	}
}
