package com.viettel.vocs.mano.model;

import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.common.file.YamlUtils;
import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.common.os.thread.JobWithTimeout;
import com.viettel.vocs.common.os.thread.SharedThreadPoolManager;
import com.viettel.vocs.common.os.thread.ThreadManager;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ApplicationCheckList {
    private static final Logger logger = LogManager.getLogger(ApplicationCheckList.class);
    private final EnumMap<CheckType, Map<String, Checker>> mapChecker = new EnumMap<>(CheckType.class);
    private final EnumMap<CheckType, Map<String, Pair<Checker, Report>>> mapInitial = new EnumMap<>(CheckType.class);
    private static final ApplicationCheckList instance = new ApplicationCheckList();
    public static ApplicationCheckList getInstance() {
        return instance;
    }

    public void registerCheckJob(CheckType type, Checker checker) {
        Map<String, Checker> checkers = mapChecker.computeIfAbsent(type, f -> new HashMap<>());
        String checkListID = checker.getCheckListID();
        Checker checker1 = checkers.computeIfAbsent(checkListID, f -> checker);
        logger.info("Checker added. type={}, id={}, count={}",
                type, checker1.getCheckListID(), checkers.size());
    }

    public void unregisterCheckJob(CheckType type, Checker checker) {
        if (!mapChecker.containsKey(type)) {
            return;
        }
        Map<String, Checker> checkers = mapChecker.get(type);
        String checkListID = checker.getCheckListID();
        Checker remove = checkers.remove(checkListID);
        if (remove != null) {
            checkers.put(checkListID, checker);
            logger.info("Checker removed. type={}, id={}, count={}",
                    type, checker.getCheckListID(), checkers.size());
        }
    }

    public <T> T initialCheckJobWithReturn(CheckType type, Checker<T> checker) {
        if (!mapInitial.containsKey(type)) {
            mapInitial.put(type, new HashMap<>());
        }
        Map<String, Pair<Checker, Report>> checkers = mapInitial.get(type);
        String checkListID = checker.getCheckListID();
        if (checkers.containsKey(checkListID)) {
            return null;
        }
        MutablePair<Checker, Report> pair = new MutablePair<>(checker, null);
        checkers.put(checkListID, pair);
        logger.info("Init job with return added. type={}, id={}, count={}", type, checker.getCheckListID(), mapInitial.size());
        Pair<Report, T> d = getReportAndReturn(type, checker);
        pair.setRight(d.getKey());
        return d.getValue();
    }

    public void initialCheckJob(boolean async, CheckType type, Checker checker) {
        mapInitial.computeIfAbsent(type, t -> new HashMap<>());
        Map<String, Pair<Checker, Report>> checkers = mapInitial.get(type);
        String checkListID = checker.getCheckListID();
        if (checkers.containsKey(checkListID))
            return;
        MutablePair<Checker, Report> pair = new MutablePair<>(checker, null);
        checkers.put(checkListID, pair);
        logger.info("Init job added. type={}, id={}, count={}", type, checker.getCheckListID(), mapInitial.size());
        if (async) {
            SharedThreadPoolManager.executeAsync(new JobWithTimeout() {
                @Override
                public String getName() {
                    return String.format("Initial check job %s_%s", type, checker.getCheckListID());
                }

                @Override
                public void run() {
                    pair.setRight(getReport(type, checker));
                }
            });
        } else {
            pair.setRight(getReport(type, checker));
        }
    }

    private <T> Pair<Report, T> getReportAndReturn(CheckType type, Checker<T> checker) {
        long l = System.currentTimeMillis();
        Report t = new Report();
        T data = null;
        t.setCheckID(checker.getCheckListID());
        t.setStartTime(TimeUtils.miliToString(l));
        t.setType(type);
        try {
            Checkable<T> checkable = checker.performCheck();
            t.setHealthy(checkable.isHealthy());
            data = checkable.getCheckDetail();
            if (isSerializable(data)) {
                t.setDetail(data);
            } else {
                t.setDetail(data);
            }
        } catch (Throwable ex) {
            logger.error(ex, ex);
            t.setHealthy(false);
            t.setDetail(ex.getMessage());
        } finally {
            t.setDuration(System.currentTimeMillis() - l);
        }
        return new ImmutablePair<>(t, data);
    }

    private Report getReport(CheckType type, Checker checker) {
        long l = System.currentTimeMillis();
        String currentFormatDateTime = TimeUtils.miliToString(l);
        Report t = new Report();
        t.setCheckID(checker.getCheckListID());
        t.setStartTime(currentFormatDateTime);
        t.setType(type);
        String name = String.format("getReport_%s_%s_%s", type, checker.getCheckListID(), currentFormatDateTime);
        CompletableFuture<Checkable> doCheck = CompletableFuture.supplyAsync(() -> {
            Checkable checkable;
            try {
                if (logger.isDebugEnabled())
                    logger.debug("Start getReport. name={}", name);
                checkable = checker.performCheck();
            } catch (Throwable ex) {
                checkable = new Checkable<>() {
                    @Override
                    public boolean isHealthy() {
                        return false;
                    }

                    @Override
                    public String getCheckDetail() {
                        return ex.getMessage();
                    }
                };
            }
            return checkable;
        }, ThreadManager.sharedThreadPoolExecutor);
        try {
            Checkable<String> checkable;
            if (checker.getCheckTimeoutSec() <= 0) {
                checkable = doCheck.get();
            } else {
                checkable = doCheck.get(checker.getCheckTimeoutSec(), TimeUnit.SECONDS);
            }
            t.setHealthy(checkable.isHealthy());
            Object data = checkable.getCheckDetail();
            if (isSerializable(data)) {
                t.setDetail(data);
            } else {
                t.setDetail(data.toString());
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Finish checklist. name={}, healthy={}", name, checkable.isHealthy());
            }
        } catch (Throwable ex) {
            if (ex instanceof TimeoutException) {
                logger.error("Timeout checklist. name={}, timeoutSec={}", name, checker.getCheckTimeoutSec());
            } else {
                logger.error("Exception checklist. name={}, ex={}", name, ex.getMessage());
            }
            doCheck.cancel(true);
            t.setHealthy(false);
            t.setDetail(ExceptionUtils.getStackTrace(ex));
        } finally {
            t.setDuration(System.currentTimeMillis() - l);
        }
        return t;
    }

    private boolean isSerializable(Object data) {
        try {
            JsonUtils.toString(data);
        } catch (Throwable ex) {
            logger.error(ex, ex);
            return false;
        }
        try {
            YamlUtils.objectToPrettyYaml(data);
        } catch (Throwable ex) {
            logger.error(ex, ex);
            return false;
        }
        return true;
    }

    public <T> List<Report> getInitialReport() {
        List<Report> ret = new ArrayList<>();
        for (Map<String, Pair<Checker, Report>> value : mapInitial.values()) {
            for (Pair<Checker, Report> pair : value.values()) {
                ret.add(pair.getValue());
            }
        }
        return ret;
    }

    public void getCheckListReport(String id, Handler<List<Report>> callback) {
        SharedThreadPoolManager.executeAsync(new JobWithTimeout() {
            @Override
            public String getName() {
                return "Get checklist " + TimeUtils.miliToString(System.currentTimeMillis()) + "_" + id;
            }

            @Override
            public void run() {
                try {
                    List<Report> checkListReport = getCheckListReport();
                    callback.handle(checkListReport);
                } catch (Throwable ex) {
                    logger.error(ex, ex);
                }
            }
        });
    }

    public List<Report> getCheckListReport() {
        List<Report> all = new ArrayList<>();
        for (Map.Entry<CheckType, Map<String, Checker>> entry : mapChecker.entrySet()) {
            CheckType type = entry.getKey();
            Map<String, Checker> value = entry.getValue();
            for (Checker checker : value.values()) {
                Report report = getReport(type, checker);
                all.add(report);
            }
        }
        return all;
    }
    public boolean verify(List<Report> reports) {
//        if (verifier == null) { // null safe
//            logger.warn("No report verifier specified. return true");
//            return true;
//        }
        try {
            return reports.stream().allMatch(Report::isHealthy);
        } catch (Throwable ex) {
            logger.error(ex, ex);
            return false;
        }
    }

    public boolean hasChecker() {
        //ignore mano notification
        boolean hasInitial = false;
        Map<String, ?> stringPairMap = mapInitial.get(CheckType.MONITOR);
        if (stringPairMap != null) {
            hasInitial = stringPairMap.keySet().stream().anyMatch(k-> !k.equalsIgnoreCase("MANO_NOTIFICATION"));
        }
        return !mapChecker.isEmpty() || hasInitial;
    }
}
