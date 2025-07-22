package com.viettel.autotest.microchassis.lib.generator;

import com.viettel.autotest.microchassis.connection.config.GeneratorConfig;
import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.MathUtils;
import com.viettel.vocs.common.log.MetricsBenchmark;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.os.thread.NameableThreadFactory;
import com.viettel.vocs.common.queue.Async2LockIIQ;
import com.viettel.vocs.common.queue.CoreQueueIIQ;
import com.viettel.vocs.common.queue.IIQ;
import com.viettel.vocs.common.queue.MpmcIIQ;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.context.ChassisNoReplyNoContext;
import com.viettel.vocs.microchassis.util.MonitorManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.viettel.vocs.common.CommonConfig.RateLimiterConfig.DEFAULT_WINDOW;

public abstract class FakeTPSGenerator<Msg extends ChassisMgrableMsg, Allocer> implements PayloadGenerator<Msg, Allocer> {
	public MetricsBenchmark dropResponse = new MetricsBenchmark();

//	tps:100Ms(call:27Ms, wait:19Ms, draw:20Ms, escape:20Ms)
//	send:881Ms
//	statis:96Ms
//	endloop:55Ms
//  client:160Ms -> line:300Ms -> server:135Ms -> line:400Ms

// anh huong cua log:
// Server recv 6220987001976239
// 82586
// 	 Server do 6220987002058825
// 46924
//Server enque 6220987002105749

	//	Server recv 6220986998194443
// 141200
//	Server do 6220986998335650
// 48500
//	Server enque 6220986998384136
// moi cau printf trung binh su dung 100Ms
	static Logger logger = LogManager.getLogger(FakeTPSGenerator.class);
	public IIQ<ChassisNoReplyNoContext<Msg>> queueMgr;

	public enum GenMode{
		queue,
		thread
	}


	ALGORITHM method = FakeTPSGenerator.ALGORITHM.LINEAR; // FakeTPSGenerator.ALGORITHM.IRWIN_HALL;
	long lastRotate;
	long window = DEFAULT_WINDOW.get(); //default 1s
	private final GeneratorConfig config;
	long nRequests = 0;
	long wait;
	long nClient = 1;

	public enum ALGORITHM {
		IRWIN_HALL,
		//		POISSON,
		LINEAR
	}

	ExecutorService rotator = Executors.newSingleThreadExecutor(new NameableThreadFactory("TPSrotator"));


	public MonitorManager mon = MonitorManager.getInstance();
	private final ByteBufAllocator bytebufAllocator;

	@Override
	public ByteBufAllocator getAllocator() {
		return bytebufAllocator;
	}

	public FakeTPSGenerator(GeneratorConfig config, ByteBufAllocator bytebufAllocator) {
		this.bytebufAllocator = bytebufAllocator;
		this.config = config;
		nRequests = config.getFakeTPS();
		switch (CommonConfig.QueueConfig.queueType.get()) {
			case "a2lock":
				queueMgr = new Async2LockIIQ<>();
				break;
			case "mpmc":
				queueMgr = new MpmcIIQ<>();
				break;
			case "core": // FakEnQ|  34946|  1,164.207|        0|   238791|  avgTime=456.900|    7104|  1,420.516|  771.846|  1,420.516|         0.0|     19343.0|     avgCurTime=328.370|
				// 457micro -> 2200 TPS
			default:
				queueMgr = new CoreQueueIIQ<>();
				break;
		}
		this.lastRotate = TimeUtils.nowNano();
		rotator.execute(() -> {
			while (true) {
				long start = TimeUtils.nowNano();
				rotate();
				TimeUtils.waitUntil(window + start);
			}
		});
		ExecutorService continuousGenerator = Executors.newFixedThreadPool(config.evaluateGenerateThreads(), new NameableThreadFactory("FakeR"));
		for (int i = 0; i < config.evaluateGenerateThreads(); i++) {
			int finalI = i;
			continuousGenerator.execute(() -> {
				logger.info("Start generator {}", finalI);
				ChassisNoReplyNoContext<Msg> ctr = null;
				long lastOfferTime = TimeUtils.nowNano();
				long accumulateRedundantTime = 0;
				long overflowTime;
				long deadline;
				long reduceWaitAmount;
				long lastClearDebt = TimeUtils.nowNano();
				while (true) { // neu fakeTPS=0
					// while true inputer.get() se tao ra request tuan tu neu co response, can tao msg doc lap

					long lastDistr = TimeUtils.nowNano();
					if(wait > 0) {
						ChassisNoReplyNoContext<Msg> r = generateServerIncomingReq();
						mon.generated(lastDistr, accumulateRedundantTime, config.getFakePayloadSize());
						long offerTime;
						if (queueMgr.offer(r) && (offerTime = TimeUtils.nowNano()) > 0) { // neu offer thanh cong thi drop -> GC
							mon.fakerEnqueued(offerTime, queueMgr.size());
						} else mon.genFulled(lastDistr);
						deadline = lastDistr + (wait - (reduceWaitAmount = Math.min(wait, accumulateRedundantTime)));
						accumulateRedundantTime -= reduceWaitAmount;
						if((overflowTime = TimeUtils.nowNano() - deadline) > 0) accumulateRedundantTime += overflowTime; // passed deadline, add to redundant for next round reduceWaitTime
						else TimeUtils.waitUntil(deadline);
						if (TimeUtils.miliPassed(lastClearDebt) > CommonConfig.StatisticConfig.intervalStatistic.get()) {
							lastClearDebt = TimeUtils.nowNano();
							accumulateRedundantTime = 0; // clear debt
						}
					} else { // infinite mode
						if (ctr == null) {
							ctr = generateServerIncomingReq(); // neu offer thanh cong thi renew, khong thi skip
						} else {
							reuseUnusedServerIncomingReq(ctr);
						}
						if (queueMgr.offer(ctr)) { // duy tri full queue size
							mon.fakerEnqueued(lastOfferTime, queueMgr.size());
							lastOfferTime = TimeUtils.nowNano();
							ctr = null;
							TimeUtils.waitSafeNano(CommonConfig.QueueConfig.DEFAULT_QUEUE_SIZE.get()); // co the enqueue toi 1tr1TPS -> thoi gian wait = tgian flush het 1 queue
						}
					}
				}
			});
		}
	}


	private synchronized long rotate() {
		nClient = Long.max(config.evaluateGenerateThreads(), 1);
		switch (method) {
			case IRWIN_HALL:
				window = MathUtils.randomIntIncluded(1, config.getNWindowToRotate()) * DEFAULT_WINDOW.get();
				nRequests = MathUtils.randomIntIncluded(1, config.getFakeTPS());
				break;
			case LINEAR:
			default:
				break;
		}

		if(nRequests > 0) {
			wait = window / (nRequests / nClient /*TPS per genThread*/); // wait on thread
			logger.info(String.format("Rotate fake generator window %ds for %d requests equal %.2f ms/req equal %.2fTPS",
				TimeUnit.NANOSECONDS.toSeconds(window), nRequests, (float) (wait) / 1_000_000L, (float) 1_000_000_000 / wait));
		} else wait = 0;
		logger.info("Wait TPS unit {}ns for {}speed", wait, nRequests);
		return TimeUtils.nowNano();
	}

	public void reuseUnusedServerIncomingReq(ChassisNoReplyNoContext<Msg> skipGenMsg){
		long createTime = TimeUtils.nowNano();
		ByteBuf bb = skipGenMsg.getInMsg().getReadOnlyContentByteBuf();
		bb.resetReaderIndex();
		skipGenMsg.getInMsg().setDecodeTime(createTime);
	}
	public ChassisNoReplyNoContext<Msg> generateServerIncomingReq() {
		return new ChassisNoReplyNoContext<>(generateRandomBinaryRequest(config.getFakePayloadSize()));
	}
}
