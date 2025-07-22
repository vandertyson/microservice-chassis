package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.limiter;

import com.viettel.vocs.microchassis.connection.config.RateLimiterConfig;
import com.viettel.vocs.microchassis.connection.exception.RateLimiterException;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.ServerCounter;
import com.viettel.vocs.common.os.TimeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.viettel.vocs.common.CommonConfig.StatisticConfig.intervalStatistic;

public class ServerLimiter {
    protected final  RateLimiterConfig config; // not null checked, if null create NoLimiter

    private static final Logger logger = LogManager.getLogger(ServerLimiter.class);

    private final ConcurrentLimiter cl;
    private final ServerCounter counter; // not null checked

    public ServerLimiter(RateLimiterConfig conf, ServerCounter counter) {
        this.config = conf;
        this.counter = counter;
        cl = new ConcurrentLimiter(this.counter)
          .setMaxConcurrent(config.maxConcurrent);
    }

    public RateLimiterConfig getConfig() {
        return config;
    }

    public void acquire() throws RateLimiterException { // equal/included waitTPS, remove waitCC
        if (config.maxTps > 0) { // 0 mean no limit
            long startLock = TimeUtils.nowNano();
            while (counter.getSentTPS() > config.maxTps) {
                if (logger.isDebugEnabled() && (TimeUtils.miliPassed(startLock) > intervalStatistic.get())) {
                    logger.debug("[Rate limiter is working]{maxTps={}}", config.maxTps);
                }
                if (TimeUtils.isTimeoutNano(startLock, config.lockTimeoutNs)) {
                    throw new RateLimiterException(String.format(
                      "Rate limiter timeout lock after %s ms. maxTps %d", TimeUtils.miliPassed(startLock), config.maxTps));
                }
                TimeUtils.waitSafeNano(config.lockCycleNs);
            }
        }
    }


    @Deprecated // server do not know ack of response -> unable to check CC speed
    private void waitConcurrent() throws RateLimiterException {
        long startLock= TimeUtils.nowNano();
        while (!cl.draw()){
            if (TimeUtils.isTimeoutNano(startLock, config.lockTimeoutNs)) {
                throw new RateLimiterException(String.format(
                  "Rate limiter timeout lock after %d ms. Current cc %d, maxConcurrent %d", config.lockTimeoutNs, counter.getSentCC(), config.maxConcurrent));
            }
            TimeUtils.waitSafeNano(config.lockCycleNs);
        }
    }

    public boolean tryAcquire() {
        return // cl.draw() &&  // ignore wait CC
          config.maxTps == 0  // 0 mean no limit
            || counter.getSentTPS() < config.maxTps;
    }
}
