package com.viettel.vocs.microchassis.topology.model.business.node;

import com.viettel.vocs.common.CommonConst;

/**
 * @author tiennn18
 */
public class Fold extends BusinessNode<SubBusiness, Group> {

	public Fold(String name, SubBusiness owner) {
		super(name);
		setOwner(owner);
	}
	public Fold(String zoneName) {
		this(zoneName, new SubBusiness(CommonConst.Name.UNKNOWN));
	}
}
