package com.viettel.vocs.microchassis.topology.model.virt.node;

import com.viettel.vocs.microchassis.topology.model.TopoEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tiennn18
 */
public abstract class AddressEntity<Parent extends AddressEntity, Child extends AddressEntity> extends TopoEntity {
	transient protected final List<Child> childList = new ArrayList<>();
	final String name;

	protected Parent owner;

	public void setOwner(Parent owner) {
		this.owner = owner;
		owner.childList.add(this);
	}

	String fullQualifyAddress;

	protected AddressEntity(String name) {
		this.name = name;
	}

	public String getFullQualifyAddress() {
		return fullQualifyAddress = (owner != null ? (name + ";" + owner.getFullQualifyAddress()) : name);
	}
}
