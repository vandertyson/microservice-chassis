package com.viettel.vocs.microchassis.topology.model.business.node;

import com.viettel.vocs.common.CommonConst;

/**
 * @author tiennn18
 */
public class Entity extends BusinessNode<Group, Entity> {
	public Entity(String name, Group owner) {
		super(name);
		setOwner(owner);
	}
	public Entity(String name) {
		this(name, new Group(CommonConst.Name.UNKNOWN));
	}
}
