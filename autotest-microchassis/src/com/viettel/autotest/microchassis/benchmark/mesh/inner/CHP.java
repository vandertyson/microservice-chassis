package com.viettel.autotest.microchassis.benchmark.mesh.inner;

import com.viettel.autotest.microchassis.benchmark.Microservice;

/**
 * @author tiennn18
 * main for Abm call Microservice
 * for((;;)); do pidstat -t -p $(pgrep -d"," -f "tiennn18.Abm") 2 | awk '{print $4,$5,$6,$7,$8,$9,$10,$11}'; done
 */
public class CHP  {
	public static void main(String[] args) throws Exception{
		System.setProperty("VDU_NAME", "chp"); // service type
		System.setProperty("VNFC_NAME", "chp-sdf23-c3t2ho"); // pod id
		System.setProperty("VNF_INSTANCE_NAME", "chp-gx-02"); // cnf/deployment name
		System.setProperty("DNS_NAME", "localhost"); // dns test @ localhost
		System.setProperty("serviceType", "forward"); // dns test @ localhost
		System.setProperty("configFolder", "/home/vht/Projecs/javawork/microservice-chassis/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/inner");
		Microservice.main(args);
	}
}
