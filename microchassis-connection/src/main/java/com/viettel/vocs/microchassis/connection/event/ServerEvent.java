package com.viettel.vocs.microchassis.connection.event;

import com.viettel.vocs.microchassis.connection.server.NettyServer;

public class ServerEvent extends InternalEvent {
	private final NettyServer server;

	public ServerEvent(EventType type, NettyServer server, String message) {
		super(type, message);
		this.server = server;
	}
	public ServerEvent(EventType type, NettyServer server) {
		super(type);
		this.server = server;
	}
	@Override
	public EventType getType() {
		return (EventType) type;
	}

	public NettyServer getServer() {
		return server;
	}

	public enum EventType implements EventCatalog {
		SERVER_UP(400),
		SERVER_FAIL_START(401);

		private final int eventCode;

		EventType(int eventCode) {
			this.eventCode = eventCode;
		}

		@Override
		public int getCode() {
			return eventCode;
		}
	}
}
