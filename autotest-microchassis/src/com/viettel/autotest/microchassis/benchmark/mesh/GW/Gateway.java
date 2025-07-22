package com.viettel.autotest.microchassis.benchmark.mesh.GW;

import com.viettel.autotest.microchassis.benchmark.Microservice;

/**
 * @author tiennn18
 * main for DB call Microservice
 * for((;;)); do pidstat -t -p $(pgrep -d"," -f "tiennn18.Gateway") 2 | awk '{print $4,$5,$6,$7,$8,$9,$10,$11}'; done
 */
public class Gateway {
	public static void main(String[] args) throws Exception{
		System.setProperty("VDU_NAME", "cgw"); // service type
		System.setProperty("VNFC_NAME", "cgw-dd230r-aad11d"); // pod id
		System.setProperty("VNF_INSTANCE_NAME", "cgw-gy-data"); // cnf/deployment name
		System.setProperty("DNS_NAME", "localhost"); // dns test @ localhost
		System.setProperty("serviceType", "generator"); // dns test @ localhost
		System.setProperty("configFolder", "/home/vht/Projecs/javawork/microservice-chassis/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/GW");
		// service

		// server
		System.setProperty("fakeTPS", "0");
//		ServiceConfig.cport=13001;
//		ServiceConfig.chost="localhost";
		// run
		Microservice.main(args);
	}
}
