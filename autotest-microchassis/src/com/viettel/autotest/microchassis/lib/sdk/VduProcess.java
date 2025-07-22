package com.viettel.autotest.microchassis.lib.sdk;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.mano.Microchassis;
import com.viettel.vocs.microchassis.codec.handler.tcp.TcpHandler;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.metrics.MetricCollector;
import com.viettel.vocs.microchassis.metrics.SeriesType;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author tiennn18
 */
public class VduProcess {
	protected static Logger logger = LogManager.getLogger(VduProcess.class);

	protected static void run(String name) {
		LogUtils.setupLog4j2();
		//start http server
		CommonConfig.JavaConfig.ETC_FOLDER.setDefault("./" + name);
		/** chassis em phát triển thêm SDK cho VDU, hướng dẫn sử dụng như trong hình
		 * hướng đến loại bỏ hoàn toàn ConnectionManager ở VDU
		 * SDK giúp tách biệt lỗi của chassis và lỗi app, khởi tạo app sạch sẽ, rõ ràng ở các client, server
		 * đã thử nghiệm tại abm */
		Microchassis chassis = new Microchassis().setManoServerId("hServer");
		try {
			chassis.start(null, false);
			chassis.openServiceMano(HttpManoServerHandler::new, null);
			chassis.openService("tcpClient",
				true, // wait port up
				TcpHandler::new, // main handler
				NettyServer::notifyStop, // shutdown hook thread
				server -> { // after service up
					MetricCollector.registerApp();
					MetricCollector.getMetric().set("testMe", SeriesType.avgT, 92332.2893629d, new MutablePair<>("tiennn18", "tiennn19"));
				});
		} catch (Exception e) {
			logger.error(e, e);
		}
	}
}
