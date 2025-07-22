package com.viettel.vocs.microchassis.topology.model.infra.node;

import com.viettel.vocs.common.CommonConst;

/**
 * @author tiennn18
 */
public class PNode extends InfraNode<Floor, Process> {

	public PNode(String name, Floor owner) {
		super(name);
		setOwner(owner);
	}
	public PNode(String name) {
		this(name, new Floor(CommonConst.Name.UNKNOWN));
	}
}
