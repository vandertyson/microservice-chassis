/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.connection.event;

import com.viettel.vocs.common.os.thread.ThreadManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InternalEventHandler implements EventHandler {
	private static final Logger logger = LogManager.getLogger(EventHandler.class);
	private final Map<Integer, Set<Consumer>> mapHandlers = new ConcurrentHashMap<>();
	private final ThreadManager.PoolExecutor executor;
	private final String id;
	public void close(){
		executor.shutdownNow();
	}
	public InternalEventHandler(@NotNull String id, @NotNull ThreadManager.PoolExecutor executor) {
		this.executor = Objects.requireNonNull(executor);
		this.id = Objects.requireNonNull(id);
	}
	@Override
	public <Event extends InternalEvent> EventHandler removeEventHandler(EventCatalog type, Consumer<Event> handler) {
		Set<Consumer> get = mapHandlers.get(type.getCode());
		if (get != null && handler != null) {
			get.remove(handler);
			logger.info("[Event handler removed]{id={}, event={}, handler={}, mapHandlers={}}", id, type, handler, mapHandlers.entrySet().stream().map(f -> f.getKey() + ": " + f.getValue().size()));
		}
		return this;
	}
//
//	public Set<Handler> getHandlers(InternalEvent type) {
//		return mapHandlers.get(type.getEventCode());
//	}

	public <Event extends InternalEvent> EventHandler addEventHandler(EventCatalog type, Consumer<Event> handler) {
		if (handler != null) {
			mapHandlers.computeIfAbsent(type.getCode(), k -> new CopyOnWriteArraySet<>()).add(handler); // set will ignore new item if duplicated
			logger.info("[Event handler added]{id={}, event={}, handler={}, mapHandlers={}}", id, type, handler,
				mapHandlers.entrySet().stream().map(f -> f.getKey() + "=" + f.getValue().size())
					.collect(Collectors.joining(", ", "[", "]")));
		}
		return this;
	}

	public <Event extends InternalEvent> EventHandler triggerEvent(Event evt) {
		if (evt != null) {
			EventCatalog type = evt.type;
			Set<Consumer> get = mapHandlers.get(type.getCode());
			if (get == null || get.isEmpty()) {
				if (logger.isDebugEnabled())
					logger.warn(String.format("[No handlers found for event %s]", type));
				return this;
			}
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Found %d handlers for event %s", get.size(), type));
			}
			get.forEach(handler -> executor.execute(() -> {
				if (logger.isDebugEnabled()) logger.debug("[Execute event] {}", evt);
				handler.accept(evt);
			}));
		}
		return this;
	}
}
