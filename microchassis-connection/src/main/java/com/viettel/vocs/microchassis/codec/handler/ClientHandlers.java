package com.viettel.vocs.microchassis.codec.handler;

public class ClientHandlers extends MultiVersionHandlers<ClientHandler> {
	public static ClientHandlers newInstance() {
		return new ClientHandlers();
	}

	public ClientHandlers setProtoVersionHandler(String version, ClientHandler handler) {
		put(version, handler);
		return this;
	}
}
