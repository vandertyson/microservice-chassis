package com.viettel.vocs.microchassis.codec.handler.tcp;

import com.viettel.vocs.microchassis.codec.context.tcp.TcpBasedContext;
import com.viettel.vocs.microchassis.codec.handler.ClientHandler;
import com.viettel.vocs.microchassis.connection.config.PeerConfig;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * TcpBasedHandler is a managed handler, not for user to call new,
 * 	user need to define itself UserTcpBasedHandler method overwrite to be called by this TcpBasedHandler class
 * @param <Message>
 * @param <CtxResponse>
 */
public abstract class TcpBasedHandler<Message extends Msg, CtxResponse extends TcpBasedContext<Message>>
	extends ClientHandler<Message, Message, CtxResponse> // client has both send and receive, so extend client and implement server and chassis to fulfill as ChassisServerHandler
	implements ServerHandler<Message, Message, CtxResponse> {
	protected static final Logger logger = LogManager.getLogger(TcpBasedHandler.class);

	protected TcpBasedHandler(PeerConfig config) {
		super(config);
	}
}

