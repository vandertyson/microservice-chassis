package com.viettel.vocs.microchassis.topology;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.topology.model.InstanceTopo;
import com.viettel.vocs.microchassis.topology.model.TopoLink;
import com.viettel.vocs.microchassis.topology.model.business.node.BusinessNode;
import com.viettel.vocs.microchassis.topology.model.infra.node.Process;
import com.viettel.vocs.microchassis.topology.model.infra.node.*;
import com.viettel.vocs.microchassis.topology.model.virt.node.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author tiennn18
 */
public interface TopoHandler {
	AtomicReference<InstanceTopo> mTopoLocal = new AtomicReference<>();

	/**
	 * add node and link to input graph, then need to return new links
	 * @param graph
	 * @return  allow return null
	 */
	default Map<String, TopoLink> topoExtLinkDiscover(InstanceTopo graph){
		return null;
	}
	BusinessNode getBusiness(); // new GrandBusiness(GrandBusiness.PredefinedGrandBusiness.OCS)

	default InfraNode getInfra() {
		return new Process(
			new PNode(CommonConfig.InstanceInfo.HOSTNAME.get(),
				new Floor(ChassisConfig.ManoConfig.instanceLocation.getVim(),
					new Zone(ChassisConfig.ManoConfig.instanceLocation.getSite(),
						new Region(CommonConfig.InstanceInfo.REGION.get())))));
	}

	default VirtNode getVirt() {
		return new CNFC(CommonConfig.InstanceInfo.VNFC_NAME.get(),
				new VDU(CommonConfig.InstanceInfo.VDU_NAME.get(),
					new CNF(CommonConfig.InstanceInfo.VNF_INSTANCE_NAME.get(),
						new NS(CommonConfig.InstanceInfo.NS_INSTANCE_NAME.get()))));
	}

	//	default void registerAsTopoNode(String id, BusinessNode bizLoc, VirtNode virtLoc){ // 1 mTopo can contain multiple main node of multiple flows
//		mTopoLocal.addNodes(new TopoNode(id, bizLoc, getInfra(), virtLoc));
//	}
//	default void registerAsTopoNode(){ // main flow of mTopo
//		registerAsTopoNode(CommonConfig.InstanceInfo.INSTANCE_RUNTIME_ID, getBusiness(), getVirt());
//	}

}
