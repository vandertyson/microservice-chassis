package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

/**
 * @author tiennn18
 * <p>
 * ConnectionMode duoc gan tren moi connection, tinh toan TPS stateless hoac shorterm memory, cap nhat vao Configure config
 * Cung cap thong tin TPS cho TrafficController qua ham goi getTps
 */
public interface MonitorableConnectionMode extends ConnectionMode {
	void updatePeerLoad(int base10kCpuLoad);
	boolean isUnderPressure();
	int getPeerLoad();
}
