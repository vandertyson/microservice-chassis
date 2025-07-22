package com.viettel.vocs.microchassis.connection.event;

import com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update.Site;

public class ClientEvent extends InternalEvent {

	private final Site<?> client;

	public ClientEvent(EventType type, Site<?> client, String message) {
		super(type, message);
		this.client = client;
	}
	public ClientEvent(EventType type, Site<?> client) {
		super(type);
		this.client = client;
	}

	@Override
	public EventType getType() {
		return (EventType) type;
	}

	public String getClientId() {
		return client.getId();
	}

	public String getServiceName() {
		return client.getHostName();
	}

	@Override
	public String toString() {
		return "ServiceEventParam{" +
			"sourceEvent='" + type + '\'' +
			", clientId='" + getClientId() + '\'' +
			", serviceName='" + getServiceName() + '\'' +
			", eventMessage='" + message + '\'' +
			'}';
	}

	public enum EventType implements EventCatalog {
		DISCOVERY_SUCCESS(300),
		CONNECT_SUCCESS(302),

		DISCOVERY_FAIL(301),
		CONNECT_FAIL(303);

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
