package com.viettel.vocs.microchassis.topology.model.business.node;

import com.viettel.vocs.microchassis.topology.model.virt.node.AddressEntity;

/**
 * @author tiennn18
 */
public abstract class BusinessNode<Parent extends BusinessNode, Child extends BusinessNode>  extends AddressEntity<Parent, Child> {

	protected BusinessNode(String name) {
		super(name);
	}
}
