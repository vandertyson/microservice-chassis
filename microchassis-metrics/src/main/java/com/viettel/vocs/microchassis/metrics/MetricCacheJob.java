package com.viettel.vocs.microchassis.metrics;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class MetricCacheJob implements Job {


    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long l = context.getFireTime().getTime();
        MetricCollector.doCaching(l);
    }
}
