package com.viettel.vocs.microchassis.topology.model.virt.node;

import com.viettel.vocs.common.CommonConst;

/**
 * @author tiennn18
 */
public class CNF extends VirtNode<NS, VDU> {
	public CNF(String name, NS owner) {
		super(name);
		setOwner(owner);
	}
	public CNF(String name) {
		this(name, new NS(CommonConst.Name.UNKNOWN));
	}
}
