package com.viettel.vocs.microchassis.depricated;

import com.viettel.vocs.microchassis.codec.handler.MultiVersionHandlers;
import com.viettel.vocs.microchassis.codec.handler.tcp.ServerHandler;

public class ServerHandlers extends MultiVersionHandlers<ServerHandler> {
	public static ServerHandlers newInstance() {
		return new ServerHandlers();
	}

	public ServerHandlers setProtoVersionHandler(String version, ServerHandler handler) {
		put(version, handler);
		return this;
	}
}
