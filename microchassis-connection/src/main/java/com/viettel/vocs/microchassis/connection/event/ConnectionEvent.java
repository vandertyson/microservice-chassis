package com.viettel.vocs.microchassis.connection.event;

import com.viettel.vocs.microchassis.connection.client.ClientConnection;

public class ConnectionEvent extends InternalEvent {
	private final ClientConnection<?, ?, ?,?> conn;

	public ConnectionEvent(EventType sourceEvent, ClientConnection<?, ?, ?,?> conn) {
		super(sourceEvent);
		this.conn = conn;
	}

	public ConnectionEvent(EventType event, ClientConnection<?, ?, ?,?> conn, String message) {
		super(event, message);
		this.conn = conn;
	}

	@Override
	public EventType getType() {
		return (EventType) type;
	}

	public ClientConnection getConnection() {
		return conn;
	}

	@Override
	public String toString() {
		return "ConnectionEventParam{" +
			"connection=" + conn +
			", sourceEvent=" + type +
			'}';
	}

	public enum EventType implements EventCatalog {
		HEALTH_CHECK_SUCCESS(200),
		HEALTH_CHECK_FAIL(201),

		CONNECTION_UP(202),
		CONNECTION_DOWN(203),
		CONNECTION_WRITE_SUCCESS(204),
		CONNECTION_WRITE_FAIL(205),

		CONNECTION_INIT_SUCCESS(206),
		CONNECTION_INIT_FAIL(207),

		TIMEOUT_ISOLATION(208),
		SERVICE_NAME_VERIFICATION_FAIL(209),
		SERVICE_NAME_VERIFICATION_SUCCESS(210);

		private final int eventCode;

		EventType(int eventCode) {
			this.eventCode = eventCode;
		}

		@Override
		public int getCode() {
			return eventCode;
		}
	}

//	@Override
//	public ConnectionEventParam setParams(Map<String, ?> params) {
//		super.setParams(params);
//		return this;
//	}
}
