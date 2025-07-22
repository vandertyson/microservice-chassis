//package com.viettel.autotest.microchassis.service.DB;
//
//import com.viettel.autotest.microchassis.lib.testInstance.Microservice;
//import com.viettel.autotest.microchassis.lib.testInstance.ServiceConfig;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//
///**
// * @author tiennn18
// * main for DB call Microservice
// * for((;;)); do pidstat -t -p $(pgrep -d"," -f "tiennn18.DB") 2 | awk '{print $4,$5,$6,$7,$8,$9,$10,$11}'; done
// */
//public class DB extends ServiceConfig {
//	protected static final Logger logger= LogManager.getLogger(DB.class);
//	public static void main(String[] args) throws Exception{
//		System.setProperty("serviceType", "db");
//		System.setProperty("VDU_NAME", "aerospike"); // service type
//		System.setProperty("VNFC_NAME", "aerospike-g0u0r-9713rydho"); // pod id
//		System.setProperty("VNF_INSTANCE_NAME", "aerospike-instance-03"); // cnf/deployment name
//		System.setProperty("DNS_NAME", "localhost"); // dns test @ localhost
//		ServiceConfig.loadConfig();
//		System.setProperty("configFolder", "/home/vht/Projecs/javawork/microservice-chassis/microchassis-demo/src/com/viettel/vocs/microchassis/tiennn18/DB");
////		ServiceConfig.handleThread=20;
//		System.setProperty("VDU_NAME", "db");
////		ServiceConfig.sport = 13000;
//		ServiceConfig.loadConfig();; // conclude config for use
//		Microservice.main(args);
//	}
//	public DB(String dbName, int targetServerPort) {
//		logger.info(String.format("Connected to DB %s@%d", dbName, targetServerPort));
////		super(dbName, targetServerPort, new DBGenerator(), null);
//	}
//}
