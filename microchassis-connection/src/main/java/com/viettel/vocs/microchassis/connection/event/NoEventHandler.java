package com.viettel.vocs.microchassis.connection.event;

import java.util.function.Consumer;

public class NoEventHandler implements EventHandler {

	@Override
	public <Event extends InternalEvent> NoEventHandler removeEventHandler(EventCatalog type, Consumer<Event> handler) {
		return this;
	}

	@Override
	public <Event extends InternalEvent> NoEventHandler addEventHandler(EventCatalog type, Consumer<Event> handler) {
		return this;
	}

	@Override
	public <Event extends InternalEvent> NoEventHandler triggerEvent(Event evt) {
		return this;
	}

	@Override
	public void close() {

	}
}
