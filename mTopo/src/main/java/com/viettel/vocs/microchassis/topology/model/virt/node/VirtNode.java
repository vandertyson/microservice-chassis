package com.viettel.vocs.microchassis.topology.model.virt.node;

public abstract class VirtNode<Parent extends VirtNode, Child extends VirtNode> extends AddressEntity<Parent, Child> {
	protected VirtNode(String name) {
		super(name);
	}
}