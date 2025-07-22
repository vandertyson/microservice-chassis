/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.codec.handler.tcp;

import com.viettel.vocs.microchassis.codec.context.tcp.TcpContext;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

/**
 * @author vttek
 */
public class LogTcpHandler extends TcpHandler {
	private static final Logger logger = LogManager.getLogger(LogTcpHandler.class);
	private final Handler<TcpContext> logHandler ;

	public LogTcpHandler(PeerConfig config) {
		this(config, ctx -> {
			byte[] content = ctx.getInData();
			logger.info(String.format("Received data from channel=%s, length=%d, msgI=%s, headers=%s, content=%s", ctx.getChannelString(), content.length, ctx.getInID(), ctx.getInMsg().headers(), Arrays.toString(content)));
		});
	}
	public LogTcpHandler(PeerConfig config, Handler<TcpContext> handler) {
		super(config);
		this.logHandler = handler;
	}

	@Override
	public void handle(TcpContext ctx) {
		// skip log of default TcpHandler
		logHandler.handle(ctx);
	}
}
