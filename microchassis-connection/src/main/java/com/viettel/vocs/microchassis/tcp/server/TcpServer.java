/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.tcp.server;

import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.connection.config.ServerConfiguration;
import com.viettel.vocs.microchassis.connection.config.mesh.MeshConfig;
import com.viettel.vocs.microchassis.connection.loadbalancing.negotiator.LBRAdapter;
import com.viettel.vocs.microchassis.tcp.codec.Msg;

public class TcpServer extends TcpBasedServer<Msg, ServerConfiguration, TcpHandler, TcpServerChannelInitializer> {

	public TcpServer(ServerConfiguration config) {
		super(config);
	}

	@Override
	public TcpServer restart(TcpHandler handlers) throws Exception {
		return (TcpServer) super.restart(handlers);
	}

	@Override
	public TcpServer start(TcpHandler handlers) throws Exception {
		return (TcpServer) super.start(handlers);
	}

	@Override
	protected TcpServerChannelInitializer newServerInitializer(TcpHandler handler) {
		return new TcpServerChannelInitializer(this, handler);
	}

	@Override
	public Msg createReq(byte[] data, String path, String msgId) {
		Msg msg = new Msg(bytebufAllocator, path, msgId);
		msg.writeFrom(data);
		return msg;
	}

	public Msg createReq(byte[] data, String path) {
		return createReq(data, path, Msg.newMsgId());
	}
	@Override
	protected Msg buildStopMsg() {
		return Msg.makeStop(bytebufAllocator);
	}
	@Override
	public LBRAdapter getNegtor() {
		return ConnectionManager.getInstance().getNegtor(
			MeshConfig.Mode.CENTER_LIMIT.equals(mesh.mode) ? this.config.lbrId : ChassisConst.STANDALONE_LBR_ID
		);
	}

}
