package com.viettel.vocs.microchassis.topology.model;


/**
 * @author tiennn18
 */
public class TopoLink extends TopoEntity { // collection of this class only use Set
	public final LinkInfo info;
	public final String id;
	public final String beaconId;
	public final boolean beaconIsSource;
	public TopoLink(TopoNode beacon, LinkInfo info) {
		this.info = info;
		id = info.id;
		this.beaconId = beacon.id;
		this.beaconIsSource = !beacon.isServer;
	}
}
