package com.viettel.vocs.microchassis.topology.model.infra.node;

import com.viettel.vocs.common.CommonConst;
import com.viettel.vocs.common.os.TimeUtils;

/**
 * @author tiennn18
 */
public class Process extends InfraNode<PNode, Process> {
	public Process(String command, PNode owner) {
		super(command);
		setOwner(owner);
	}
	public Process(PNode owner) {
		this(String.format("%d_%d", CommonConst.SystemConst.PID, TimeUtils.nowNano()), owner);
	}
	public Process() {
		this(new PNode(CommonConst.Name.UNKNOWN));
	}
	public Process(String command) {
		this(command, new PNode(CommonConst.Name.UNKNOWN));
	}
}
