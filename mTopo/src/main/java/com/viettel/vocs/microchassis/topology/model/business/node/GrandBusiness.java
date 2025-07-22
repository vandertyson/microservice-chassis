package com.viettel.vocs.microchassis.topology.model.business.node;

/**
 * @author tiennn18
 */

public class GrandBusiness extends BusinessNode<GrandBusiness, SubBusiness> {
	public enum PredefinedGrandBusiness{
		OCS,
		CCS
	}
	public GrandBusiness(PredefinedGrandBusiness businessName) {
		this(businessName.name());
	}
	public GrandBusiness(String businessName) {
		super(businessName);
	}
}
