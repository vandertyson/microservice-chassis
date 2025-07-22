package com.viettel.vocs.microchassis.topology.model.infra.node;

import com.viettel.vocs.common.CommonConst;

/**
 * @author tiennn18
 */
public class Zone extends InfraNode<Region, Floor> {
	public Zone(String name, Region owner) {
		super(name);
		setOwner(owner);
	}
	public Zone(String name) {
		this(name, new Region(CommonConst.Name.UNKNOWN));
	}
}
