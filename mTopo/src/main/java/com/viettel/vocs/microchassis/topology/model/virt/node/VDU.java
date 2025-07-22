package com.viettel.vocs.microchassis.topology.model.virt.node;

import com.viettel.vocs.common.CommonConst;

/**
 * @author tiennn18
 */
public class VDU extends VirtNode<CNF, CNFC> {
	public VDU(String name, CNF owner) {
		super(name);
		setOwner(owner);
	}
	public VDU(String name) {
		this(name, new CNF(CommonConst.Name.UNKNOWN));
	}
}
