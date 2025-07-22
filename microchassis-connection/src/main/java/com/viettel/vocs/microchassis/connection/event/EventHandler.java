package com.viettel.vocs.microchassis.connection.event;

import java.util.function.Consumer;

/**
 * @author vttek
 */
public interface EventHandler {
	<Event extends InternalEvent> EventHandler removeEventHandler(EventCatalog type, Consumer<Event> handler);

	<Event extends InternalEvent> EventHandler addEventHandler(EventCatalog type, Consumer<Event> handler);

	<Event extends InternalEvent> EventHandler triggerEvent(Event evt);
	void close();
}
