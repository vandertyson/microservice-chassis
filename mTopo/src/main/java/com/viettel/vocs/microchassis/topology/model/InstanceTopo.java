package com.viettel.vocs.microchassis.topology.model;

import com.viettel.vocs.microchassis.topology.model.business.node.BusinessNode;
import com.viettel.vocs.microchassis.topology.model.infra.node.InfraNode;
import com.viettel.vocs.microchassis.topology.model.virt.node.VirtNode;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author tiennn18
 */
public class InstanceTopo extends TopoEntity {

	public final BusinessNode businessAddress;
	public final InfraNode infraAddress;
	public final VirtNode virtAddress;
	public final Map<String /*uuid*/, TopoNode> nodes = new ConcurrentHashMap<>();
	public final Map<String /*uuid*/, TopoLink> links = new ConcurrentHashMap<>();

	public InstanceTopo(BusinessNode businessAddress, InfraNode infraAddress, VirtNode virtAddress) {
		this.businessAddress = businessAddress;
		businessAddress.getFullQualifyAddress();
		this.infraAddress = infraAddress;
		infraAddress.getFullQualifyAddress();
		this.virtAddress = virtAddress;
		virtAddress.getFullQualifyAddress();
	}

	//	public TopoGraph addLinks(TopoNode serverOwner, ServerAPI... servers){
//		Arrays.stream(servers).forEach(server -> server.countEndpoints().forEach((e, s) -> { // each client e connect c channels to server
//					this.links.put(server.getId(), new TopoLink(false, serverOwner, s.getTopoLinkInfo()));
//			}));
//		return this;
//	}
//	public TopoGraph addLinks(TopoNode clientOwner, SiteAPI... clients){
//		Arrays.stream(clients).forEach(client -> client.countEndpoints().forEach((e, c) ->	{// each server e connect c channels to client
//			this.links.put(client.getId(), new TopoLink(true, clientOwner, c.getTopoLinkInfo()));
//		}));
//		return this;
//	}
	public InstanceTopo addLinks(Collection<TopoLink> links){
		links.forEach(link -> this.links.put(link.id, link));
		return this;
	}
	public InstanceTopo addLinks(TopoLink... links){
		return addLinks(Arrays.stream(links).collect(Collectors.toList()));
	}
	public InstanceTopo addNodes(Collection<TopoNode> nodes){
		nodes.forEach(node -> this.nodes.put(node.id, node));
		return this;
	}
	public InstanceTopo addNodes(TopoNode... nodes){
		return addNodes(Arrays.stream(nodes).collect(Collectors.toList()));
	}
//	public TopoGraph connect(TopoNode source, TopoNode dest){
//		addNodes(source, dest);
//		addLinks(new TopoLink(true, source, new LinkInfo()));
//		return this;
//	}

//	@Override
//	public String toString() {
//		Map<String, Object> finalObj = new HashMap<>();
//		finalObj.put("nodes", nodes);
//		finalObj.put("links", links.values().stream().map(l-> Triple.of(l.id, l.src.id, l.dest.id)).collect(Collectors.toMap(Triple::getLeft, t -> Pair.of(t.getMiddle(), t.getRight()))));
//		return JsonUtils.getEncoder().toJson(finalObj);
//	}
}
