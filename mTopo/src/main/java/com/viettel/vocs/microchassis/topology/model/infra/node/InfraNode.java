package com.viettel.vocs.microchassis.topology.model.infra.node;

import com.viettel.vocs.microchassis.topology.model.virt.node.AddressEntity;

/**
 * @author tiennn18
 */
public abstract class InfraNode<Parent extends InfraNode, Child extends InfraNode>  extends AddressEntity<Parent, Child> {
	protected InfraNode(String name) {
		super(name);
	}
}
