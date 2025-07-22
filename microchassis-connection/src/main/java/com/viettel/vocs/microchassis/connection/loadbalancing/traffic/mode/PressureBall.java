package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

/**
 * @author tiennn18
 * Pressure voi method press giong nhu viec nhan (press) thu xem phia duoi cung (hard) hay mem (soft) de tiep tuc bom (pump) TPS vao, nhu bom bong bay
 * Pressure thiet ke theo next chain, sau khi check press 1 layer thi tiep theo layer khac
 */
public class PressureBall {
	public int tps;
	public int cc;

	PressureBall(int tps, int cc) {
		this.tps = tps;
		this.cc = cc;
	}
}
