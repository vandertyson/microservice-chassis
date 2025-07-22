package com.viettel.vocs.microchassis.topology.model;

import com.viettel.vocs.common.IDfy;

/**
 * @author tiennn18
 */
public class TopoNode extends TopoEntity { // collection of this class only use Set
	final public String id;
	final boolean isServer;

	public TopoNode(String id, boolean isServer) {
		this.id = id;
		this.isServer = isServer;
	}
	public TopoNode(boolean isServer) {
		 this(IDfy.generateNewId(), isServer);
	}
}
