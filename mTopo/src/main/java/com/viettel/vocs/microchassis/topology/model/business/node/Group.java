package com.viettel.vocs.microchassis.topology.model.business.node;

import com.viettel.vocs.common.CommonConst;

/**
 * @author tiennn18
 */
public class Group extends BusinessNode<Fold, Entity> {
	public Group(String name, Fold owner) {
		super(name);
		setOwner(owner);
	}
	public Group(String zoneName) {
		this(zoneName, new Fold(CommonConst.Name.UNKNOWN));
	}
}
