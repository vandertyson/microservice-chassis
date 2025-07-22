package com.viettel.autotest.microchassis.benchmark.mesh.GW;

import com.viettel.vocs.common.log.MetricsBenchmark;
import com.viettel.vocs.common.os.thread.NameableThreadFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author tiennn18
 * client only to test lag at tcpserver (based on Gateway without server dequeue
 */
public class LB implements Runnable {
	static final Logger logger = LogManager.getLogger(LB.class);
//	private final IIQ<TcpReplyContext> queueMgr = Server.queueMgr;
	int nWorker = 2;
	ExecutorService deqPool = Executors.newFixedThreadPool(nWorker, new NameableThreadFactory("LB-flush"));
	MetricsBenchmark mb = new MetricsBenchmark();
	MetricsBenchmark sendMb = new MetricsBenchmark();

	@Override
	public void run() {
		for (int i = 0; i < nWorker; i++) {
			logger.info("Start thread " + i);
//			deqPool.execute(() -> {
////				String clientId = StringManipulation.randomString(3);
//				long lastPol = TimeUtils.nowNano();
//				while (true) {
//					if (queueMgr.size() > 0) {
//						lastPol = TimeUtils.nowNano();
//						TcpReplyContext ctr = queueMgr.poll();
//						if (ctr != null && ctr.getInMsg() != null && ctr.getInMsg().getContent() != null) {
//							if (ServiceConfig.clientMode.equals("async")) {
////								ctr.getInMsg().getContent().setLong(Const.byteTimeSendFromLastClient, TimeUtils.nowNano());
//								client.sendAsync(ctr.getInMsg().copy(), new Server.AsyncCallback() {
//									@Override
//									public void onSuccess(Msg responseMsg) {
////										sendMb.statisticMetris(responseMsg.getContent().getLong(Const.byteTimeSendFromLastClient), 0, "Async");
//									}
//
//									@Override
//									public void onFail() {
////										dropMb.statisticMetris()
//									}
//								});
////								mb.statisticMetris(lastPol, 0, "PollSend");
//							} else {
//								client.sendSync(ctr.getInMsg().copy());
//								mb.statisticMetris(lastPol, 0, "Sync");
//							}
////							System.out.println("Thread run "+ clientId);
//							ctr.release();
//						} else TimeUtils.waitSafeNano(1000L);
//					}
//
//				}
//			});
		}
	}

	public static void main2(String[] args) {
		// server


		// client

//		ServiceConfig.cport = 13000;
//		ServiceConfig.chost = "localhost";
		// run
//		ChassisAutotestConfig.GeneratorConfig.generateThreads.setDefault(1L);
//		FakeTPSGenerator.getInstance(); // autorun enqueue
		new LB().run();
	}
}
