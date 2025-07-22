package com.viettel.vocs.microchassis.topology.model.virt.node;

import com.viettel.vocs.common.CommonConst;

/**
 * @author tiennn18
 */
public class CNFC extends VirtNode<VDU, CNFC> {
	public CNFC(String name, VDU owner) {
		super(name);
		setOwner(owner);
	}
	public CNFC(String name) {
		this(name, new VDU(CommonConst.Name.UNKNOWN));
	}
}
