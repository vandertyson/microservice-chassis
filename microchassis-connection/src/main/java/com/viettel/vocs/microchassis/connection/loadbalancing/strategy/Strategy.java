package com.viettel.vocs.microchassis.connection.loadbalancing.strategy;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author tiennn18
 */
public abstract class Strategy<Destination> {
	@Getter protected final Map<String, Destination> destMap = new ConcurrentHashMap<>(); // ready connections from LB, connection co the null khi moi khoi tao


	/**
	 * self discrete call refresh based on Configure object
	 * //trigger when destMap might (or not) changed, use this to check changes
	 */
	public abstract void refresh();

	/**
	 * self continuous call refresh based on Configure object, for LB strategy thread
	 */
	public abstract void intervalRefresh();
}
