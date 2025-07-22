package com.viettel.vocs.microchassis.serviceRegistry.codec;

import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.connection.config.mesh.ServerMeshConfig;
import com.viettel.vocs.microchassis.base.Endpoint;
import com.viettel.vocs.common.IDfy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.stream.Collectors;

/**
 * @author tiennn18
 */
public class LBRServerInfo extends LBRPeer implements IDfy {
	private static final Logger logger = LogManager.getLogger(LBRServerInfo.class);
	public final ServerMeshConfig.ServerDMirror dconfig = new ServerMeshConfig.ServerDMirror();
	public long lastPoll;
	/**
	 * constructor duoc goi 1 lan duy nhat khi register, LBR va CNF_Listener chi duoc dung lai khong duoc tao moi
	 */

	public LBRServerInfo() { // for encode decode
	}
	public LBRServerInfo(String serverId, Endpoint serverEndpoint){
		super(serverId, serverEndpoint);
		logger.info("Create server with dest {}", serverEndpoint);
	}
	public void write(NettyServer server){ // update from nettyServer
		currentMap = server.countIps(); // for statefull relocate
		dconfig.read(server.getMesh());
		logger.info("Server {} update max {} min {} host {}", server.getConfig().id, dconfig.maxConnection, dconfig.minConnection, dconfig.maxHost);
	}
	public void lbrServerRead(LBRServerInfo lbrClientSentServer){
		dconfig.read(lbrClientSentServer.dconfig);
	}
	public void copy(LBRServerInfo source){ // ham nay ghi de targetMap, chi su dung o client
		logger.info("copy targetMap {} -> {}", targetMap, source.targetMap);
		targetMap = source.targetMap;
		dconfig.read(source.dconfig);
		id = source.id;
		endpoint = source.endpoint;
	}
	public String status(){
		return String.format("%s", targetMap.entrySet().stream().map(e-> e.getKey()+":"+e.getValue()).collect(Collectors.joining(",")));
	}
}
