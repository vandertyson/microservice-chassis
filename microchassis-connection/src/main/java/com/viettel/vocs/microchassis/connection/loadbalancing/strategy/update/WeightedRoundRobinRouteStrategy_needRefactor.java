package com.viettel.vocs.microchassis.connection.loadbalancing.strategy.update;

import com.viettel.vocs.common.MathUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.depricated.ArraySelectionStrategy;
import org.apache.commons.math3.util.ArithmeticUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class WeightedRoundRobinRouteStrategy_needRefactor {
    private static final Logger logger = LogManager.getLogger(WeightedRoundRobinRouteStrategy_needRefactor.class);
    private static int scale = 2;
    private static final DecimalFormat decimalFormat = new DecimalFormat("###,##0.000");

    public static void main(String[] args) throws InterruptedException, FileNotFoundException {
        LogUtils.setupLog4j2();
        logger.info("Henlo frend");
        Map<String, Integer> mapWeight = new HashMap<>();
        mapWeight.put("service1", 3);
        mapWeight.put("service2", 7);
        List<Connection> pairs = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            pairs.add(new Connection("c1_" + i, "service1"));
            pairs.add(new Connection("c2_" + i, "service2"));
        }
        long avg = 0;
        int countRun = 10_000;
        for (int i = 0; i < countRun; i++) {
            long t = System.nanoTime();
            update(pairs, mapWeight);
            avg += System.nanoTime() - t;
        }
        logger.info("Update time {} ms", avg / countRun / 1e6);
        Connection[] masks = update(pairs, mapWeight);
        Map<String, Integer> mapStat = new HashMap<>();
        Map<String, Integer> serviceStat = new HashMap<>();
        for (Connection mask : masks) {
            mapStat.compute(mask.id, (k,v)-> v!=null? v+1 : 1);
            serviceStat.compute(mask.getBaseServiceName(), (k,v) -> v !=null ? v + 1 : 1);
        }
        logger.info("maskLeng={}. By service {}. Stat: \n{}. \naslist\n{}",
                masks.length,
                serviceStat,
                mapStat.entrySet().stream().map(f -> f.getKey() + "=" + f.getValue()).collect(Collectors.joining("\n")),
                Arrays.asList(masks).stream().map(f -> f.id).collect(Collectors.joining(", ")));

        AtomicLong tps = new AtomicLong();
        ArraySelectionStrategy<Connection> strategy = new ArraySelectionStrategy<>() {
            AtomicLong counter = new AtomicLong();

            @Override
            public Connection select(Connection... selections) {
                long l = counter.incrementAndGet();
                if (l == selections.length - 1) {
                    counter.set(0);
                }
                int i = (int) (l % selections.length);
                return selections[i];
            }
        };
        logger.info("USE {}", strategy.getClass());
        WRR strat = new WRR(pairs, mapWeight);
        for (int i = 0; i < ChassisConfig.PerformanceConfig.BUSINESS_HANDLE_THREAD.check(); i++) {
            Executors.newSingleThreadExecutor().execute(() -> {
                while (true) {
                    try {
                        Connection select = ChassisConfig.RouteConfig.SELECT_WITH_ROUTE_KEY.get()
                                ? strat.select()
                                : strategy.select(masks);
                        tps.incrementAndGet();
                    } catch (Exception ex) {
                        logger.error(ex, ex);
                    }
                }
            });
        }

        while (true) {
            TimeUtils.waitSafeMili(1000);
            logger.info("Tps: {}", decimalFormat.format(tps.get()));
            if (ChassisConfig.RouteConfig.SELECT_WITH_ROUTE_KEY.get()) logger.info("Stat. Count={}", strat.mapStat);
            tps.set(0);
        }

    }

    public static class Connection {
        public String id;
        private String BaseServiceName;

        public Connection(String id, String baseServiceName) {
            this.id = id;
            BaseServiceName = baseServiceName;
        }

        public void send() {
            for (int i = 0; i < 1_000; i++) {
                int x = i * i;
            }
        }

        public long getWarmUpStart() {
            return 0;
        }

        public String getBaseServiceName() {
            return BaseServiceName;
        }

        public long getWarmUpDurationMs() {
            return 0;
        }
    }

    public static class WRR {
        private final List<Connection> connections;
        private final Map<String, Integer> weights;
        private final Map<String, Integer> counts;
        private final AtomicLong serviceCounter = new AtomicLong();
        private final List<String> selection;
        private final int size;
        private final Map<String, List<Connection>> mapConnByservice;
        private final Map<String, AtomicLong> mapInternalCounter;
        public final Map<String, AtomicLong> mapStat = new HashMap<>();

        public WRR(List<Connection> connections, Map<String, Integer> weights) {
            this.connections = connections;
            this.weights = weights;
            selection = weights.entrySet().stream()
                    .sorted((Map.Entry<String, Integer> arg0, Map.Entry<String, Integer> arg1) -> arg1.getValue() - arg0.getValue())
                    .map(Map.Entry::getKey).collect(Collectors.toList());
            size = selection.size();
            counts = new HashMap<>();
            mapInternalCounter = new HashMap<>();
            for (String s : selection) {
                counts.put(s, weights.get(s));
                mapInternalCounter.put(s, new AtomicLong());
                mapStat.put(s, new AtomicLong());
            }
            mapConnByservice = new HashMap<>();
            for (String s : selection) {
                List<Connection> collect = connections.stream().filter(f -> f.BaseServiceName.equals(s)).collect(Collectors.toList());
                mapConnByservice.put(s, collect);
            }
        }

        public Connection select() {
            long l = serviceCounter.incrementAndGet();
            if (l >= Long.MAX_VALUE) {
                serviceCounter.set(0);
            }
            int i = (int) (l % size);
            String s;
            Integer remain;
            serviceCounter.set(0);
            s = selection.get(i);
            remain = counts.get(s);
            if (remain == 1) {
                counts.put(s, weights.get(s));
            } else {
                counts.put(s, remain - 1);
            }
            List<Connection> connections = mapConnByservice.get(s);
            long l1 = mapInternalCounter.get(s).incrementAndGet();
            if (l1 == Long.MAX_VALUE) {
                l1 = 0;
            }
            int idx = (int) (l1 % connections.size());
            mapStat.get(s).incrementAndGet();
            return connections.get(idx);
        }
    }

    static synchronized Connection[] update(List<Connection> pairs, Map<String, Integer> weightedServices) {
        if (pairs == null || pairs.isEmpty()) {
            logger.warn("Empty connection");
            return null;
        }
        Map<String, Integer> filtered = weightedServices.entrySet().stream()
                .filter(f -> f.getValue() != 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (weightedServices.size() > 1) {
            //tao map weight theo service name
            //ignore cac service name co weight = 0

            // tao mask cho level service name
            Object[] maskService = generateWeightedMask(new HashMap<>(filtered));

            //tao internal mask cho tung service name
            Map<String, Connection[]> internalWeight = new HashMap<>();
            for (String serviceName : filtered.keySet()) {
                Connection[] mask = generateInternalServiceMask(pairs
                        .stream()
                        .filter(f -> f.getBaseServiceName().equals(serviceName))
                        .collect(Collectors.toList()));
                internalWeight.put(serviceName, mask);
            }

            //tao mask tong
            //Dinh ly thang du Trung Hoa: tim k sao qcho a[i]k chia het cho b[i]
            // ai la weight cua service i
            // bi la internal maskSize cua service i
            // k = bcnn(bi/ucln(ai,bi)

            List<Integer> arrs = new ArrayList<>(maskService.length);
            for (Map.Entry<String, Integer> entry : weightedServices.entrySet()) {
                int a = entry.getValue();
                int b = internalWeight.get(entry.getKey()).length;
                int i = b / ArithmeticUtils.gcd(a, b);
                arrs.add(i);
            }
            int k = findLcm(arrs, 0);

            //totalSize = sum(k*weight)
            Map<String, Integer> weightStatus = new HashMap<>();
            Map<String, Integer> counterStatus = new HashMap<>();
            for (Map.Entry<String, Connection[]> entry : internalWeight.entrySet()) {
                Integer weight = filtered.get(entry.getKey());
                if (weight != null) {
                    weightStatus.put(entry.getKey(), weight * k);
                    counterStatus.put(entry.getKey(), 0);
                }
            }
            int totalSize = weightStatus.values().stream().mapToInt(f -> f).sum();

            //Weighted round robin
            Connection[] finalMask = new Connection[totalSize];
            int serviceCounter = 0;

            for (int i = 0; i < finalMask.length; i++) {
                int serviceIndex = serviceCounter % maskService.length;
                String serviceName = String.valueOf(maskService[serviceIndex]);
                Integer existed = weightStatus.get(serviceName);
                if (existed == 0) {
                    serviceCounter = 0;
                    i--;
                    continue;
                }
                Integer connectionCounter = counterStatus.get(serviceName);
                if (connectionCounter < weightStatus.get(serviceName)) {
                    Connection[] connections = internalWeight.get(serviceName);
                    int connectionIndex = connectionCounter % connections.length;
                    finalMask[i] = connections[connectionIndex];
                    counterStatus.put(serviceName, connectionCounter + 1);
                    weightStatus.put(serviceName, existed--);
                } else {
                    i--;
                }
                serviceCounter++;
            }
            return finalMask;
        } else {
            return generateInternalServiceMask(pairs);
        }
    }

    private static int findLcm(List<Integer> listInt, int idx) {
        if (idx == listInt.size() - 1) {
            return listInt.get(idx);
        }
        int a = listInt.get(idx);
        int b = findLcm(listInt, idx + 1);
        return (a * b / ArithmeticUtils.gcd(a, b));
    }


    private static Connection[] generateInternalServiceMask(List<Connection> pairs) {
        Map<Connection, Integer> mapCount = new HashMap<>();
        int maskSize = 0;
        int warm = 0;
        int cold = 0;
        for (int j = 0; j < pairs.size(); j++) {
            Connection pair = pairs.get(j);
            long delta = System.currentTimeMillis() - pair.getWarmUpStart();
            long upTime = pair.getWarmUpDurationMs() - delta;
            int baseRate = (int) (Math.pow(10.0, scale));
            if (upTime < 0) {
                //da het thoi gian warm up thi mac dinh trong so la 10
                mapCount.put(pair, baseRate);
                maskSize += baseRate;
                cold++;
            } else {
                warm++;
                //trong so bang ti le giua thoi tong thoi gian warmup cau hinh chia cho thoi uptime
                int count = (int) (baseRate * MathUtils.round(delta * 1.0 / pair.getWarmUpDurationMs(), scale));
                int max = Math.max(1, count);
                mapCount.put(pair, max);
                maskSize += max;
            }
        }
        //build mask
        boolean isBalanced = mapCount.values().stream().distinct().count() == 1;
        Connection[] newMask = new Connection[pairs.size()];
        if (warm == 0 || cold == 0 || isBalanced) {
            for (int i = 0; i < pairs.size(); i++) {
                newMask[i] = pairs.get(i);
            }
        } else {
            logger.info("[Load balancing weight update]{mapCountSize=" + mapCount.size()
                    + ", maskSize=" + maskSize
                    + ", mapCount=" + mapCount.entrySet()
                    + "}");
            newMask = generateWeightedMask(mapCount);
        }
        return newMask;
    }

    private static <E> E[] generateWeightedMask(Map<E, Integer> weights) {
        boolean isBalanced = weights.values().stream().distinct().count() == 1;
        if (isBalanced) {
            Object[] ret = new Object[weights.size()];
            for (int i = 0; i < weights.size(); i++) {
                ret[i] = weights.get(i);
            }
            return (E[]) ret;
        }
        int maskSize = weights.values().stream().mapToInt(i -> i).sum();
        Object[] newMask = new Object[maskSize];
        List<E> collect = weights.entrySet().stream()
                .sorted((Map.Entry<E, Integer> arg0, Map.Entry<E, Integer> arg1) -> arg1.getValue() - arg0.getValue())
                .map(Map.Entry::getKey).collect(Collectors.toList());

        int j = 0;
        for (int i = 0; i < maskSize; i++) {
            E selection = collect.get(j);
            if (weights.get(selection) == 0) {
                j = 0;
                selection = collect.get(j);
                while (weights.get(selection) == 0) {
                    j++;
                    selection = collect.get(j);
                }
            }
            newMask[i] = selection;
            weights.compute(selection, (k,v) -> v - 1);
            j++;
            if (j == collect.size()) {
                j = 0;
            }
        }
        E[] result = (E[]) newMask;
        return result;
    }

}
