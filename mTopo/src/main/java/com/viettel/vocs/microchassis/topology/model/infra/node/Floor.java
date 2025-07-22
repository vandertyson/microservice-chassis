package com.viettel.vocs.microchassis.topology.model.infra.node;

import com.viettel.vocs.common.CommonConst;

/**
 * @author tiennn18
 */
public class Floor extends InfraNode<Zone, PNode> {
	public Floor(String name, Zone owner) {
		super(name);
		setOwner(owner);
	}
	public Floor(String name) {
		this(name, new Zone(CommonConst.Name.UNKNOWN));
	}
}
