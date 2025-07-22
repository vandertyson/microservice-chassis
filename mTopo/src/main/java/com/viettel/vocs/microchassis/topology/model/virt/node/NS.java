package com.viettel.vocs.microchassis.topology.model.virt.node;

/**
 * @author tiennn18
 */
public class NS extends VirtNode<NS, CNF> {
	public enum PredefinedNS{
		OCS,
		CCS
	}
	public NS(PredefinedNS name) {
		this(name.name());
	}
	public NS(String name) {
		super(name);
	}
}
