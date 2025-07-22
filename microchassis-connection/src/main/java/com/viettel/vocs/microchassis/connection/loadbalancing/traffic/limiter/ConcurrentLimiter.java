package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter;

import com.viettel.vocs.common.os.RateClock;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author tiennn18
 *
 * ConcurrentLimiter la lop phu quan ly doi tuong hien tai
 * 	1 dang limiter theo chieu ngang
 * 	moi TrafficLimiter di kem 1 ConcurrentManager va tang giam concurent theo Counter, cau hinh theo strategy va LBNegotiator
 */
public class ConcurrentLimiter {
	private final AtomicInteger maxConcurrent = new AtomicInteger(ChassisConfig.RouteConfig.ConnectionRateLimiterConfig.DEFAULT_MAX_CONCURRENT.getInt());

	public PeerCounter getCounter() {
		return counter;
	}

	private final PeerCounter counter;

	public ConcurrentLimiter setMaxConcurrent(int maxConcurrent) {
		if(maxConcurrent>=0) { // allow set to 0 to unblock CC wait
			this.maxConcurrent.set(maxConcurrent);
		}
		return this;
	}
	public ConcurrentLimiter(PeerCounter counter){
		this.counter = counter;
	}
	public long currentConcurrent(){
		return counter.getRecentSentCC();
	}
	public boolean draw(){
		return maxConcurrent.get() == 0 // if 0 mean bypass Back CC
			|| currentConcurrent() < maxConcurrent.get();
	}
	public synchronized void waitConcurrent(TimeUtils.NanoBeacon startBeacon) throws TimeoutException { // need FIFO -> synchronized;
		if(maxConcurrent.get() > 0) {
			while (!draw() && TimeUtils.waitSafeNano(RateClock.TICK)) {
				/*Neu concurrent = 1 -> tuong duong sync -> async phai 2*/
				if(startBeacon.isTimeout()) throw new TimeoutException("Acquire failed on ConcurrentLimiter.waitConcurrent()");
			}
		}
		// else pass through
	}
	public void close(){
		maxConcurrent.set(0); // to skip waitCC
	}
}
