package com.viettel.vocs.microchassis.codec.handler;

/**
 * @author hungtv7
 */
public interface AsyncHandler<HandleArgType> extends TimeoutHandler, Handler<HandleArgType> {
	/**
	 * this handler for peer client and peer server
	 * it has 2 basic handler is timeout(String msgId) and handle(HandleArgType arg) API
	 * this handler is basic for chassis' semantic, because msgId is traditional of microchassis
	 */
}

