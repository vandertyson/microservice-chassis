package com.viettel.vocs.microchassis.topology.model.business.node;

import com.viettel.vocs.common.CommonConst;

/**
 * @author tiennn18
 */
public class SubBusiness extends BusinessNode<GrandBusiness, Fold> {

	public SubBusiness(String name, GrandBusiness owner) {
		super(name);
		setOwner(owner);

	}
	public SubBusiness(String zoneName) {
		this(zoneName, new GrandBusiness(CommonConst.Name.UNKNOWN));
	}
}
