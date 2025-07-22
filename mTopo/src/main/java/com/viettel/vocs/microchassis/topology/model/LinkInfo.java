package com.viettel.vocs.microchassis.topology.model;

import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.microchassis.topology.model.business.link.BusinessLink;
import com.viettel.vocs.microchassis.topology.model.infra.link.InfraLink;
import com.viettel.vocs.microchassis.topology.model.virt.link.VirtLink;

/**
 * @author tiennn18
 */
public class LinkInfo {
	public final String id;
	public final String owner;
	public final Endpoint dest;
	public final Endpoint source;
	public long connected = 0;
	public long desired = 0;
	BusinessLink businessAddress;
	InfraLink infraAddress;
	VirtLink virtAddress;
	public LinkInfo(String linkId, Endpoint src, Endpoint dst, TopoNode destNode) {
		id = linkId;
		owner = destNode.id;
		dest = dst;
		source = src;
	}
}
