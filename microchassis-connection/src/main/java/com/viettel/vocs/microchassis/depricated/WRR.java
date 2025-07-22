package com.viettel.vocs.microchassis.depricated;

import com.viettel.vocs.common.MathUtils;
import org.apache.commons.math3.util.ArithmeticUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class WRR {

    private static final Logger logger = LogManager.getLogger(WRR.class);

    public interface HasServiceName {
        String getServiceName();
    }

    public static synchronized HasServiceName[] update(List<HasServiceName> pairs, Map<String, Integer> weightedServices) {
        if (pairs == null || pairs.isEmpty()) {
            logger.warn("Empty connection");
            return null;
        }
        if (weightedServices == null) {
            return generateInternalServiceMask(pairs);
        }
        //ignore cac service name co weight = 0 hoac chua co connection nao
        List<String> hasConn = pairs.stream().map(HasServiceName::getServiceName).distinct().collect(Collectors.toList());
        Map<String, Integer> filtered = weightedServices.entrySet().stream()
                .filter(f -> f.getValue() != 0)
                .filter(f -> hasConn.contains(f.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // tao mask cho level service name
        Object[] maskService = generateWeightedMask(new HashMap<>(filtered));

        //tao internal mask cho tung service name
        Map<String, HasServiceName[]> internalWeight = new HashMap<>();
        for (String serviceName : filtered.keySet()) {
            HasServiceName[] mask = generateInternalServiceMask(pairs
                    .stream()
                    .filter(f -> Objects.equals(f.getServiceName(), serviceName))
                    .collect(Collectors.toList()));
            internalWeight.put(serviceName, mask);
        }

        //truong hop co 1 host name thi thoi lay luon mask cho nhanh
        if (internalWeight.size() == 1) {
            return internalWeight.get(maskService[0].toString());
        }

        //tao mask tong
        //Dinh ly thang du Trung Hoa: tim k sao cho a[i]k chia het cho b[i]
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
        int k = MathUtils.findLcm(arrs, 0);

        //totalSize = sum(k*weight)
        Map<String, Integer> weightStatus = new HashMap<>();
        Map<String, Integer> counterStatus = new HashMap<>();
        for (Map.Entry<String, HasServiceName[]> entry : internalWeight.entrySet()) {
            Integer weight = filtered.get(entry.getKey());
            if (weight != null) {
                weightStatus.put(entry.getKey(), weight * k);
                counterStatus.put(entry.getKey(), 0);
            }
        }
        int totalSize = weightStatus.values().stream().mapToInt(f -> f).sum();
        logger.info("[Service monitor mask update]{totalSize={}}", totalSize);
        //Weighted round robin
        HasServiceName[] finalMask = new HasServiceName[totalSize];
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
            HasServiceName[] connections = internalWeight.get(serviceName);
            int connectionIndex = connectionCounter % connections.length;
            if (connectionIndex < weightStatus.get(serviceName)) {
                finalMask[i] = connections[connectionIndex];
                counterStatus.put(serviceName, connectionCounter + 1);
            } else {
                counterStatus.put(serviceName, 0);
                i--;
            }
            serviceCounter++;
        }
        return finalMask;
    }

    private static HasServiceName[] generateInternalServiceMask(List<HasServiceName> pairs) {
        Map<HasServiceName, Integer> mapCount = new HashMap<>();
        for (int j = 0; j < pairs.size(); j++) {
            HasServiceName pair = pairs.get(j);
            mapCount.put(pair, 1);
        }
        //build mask
        boolean isBalanced = mapCount.values().stream().distinct().count() == 1;
        HasServiceName[] newMask = new HasServiceName[pairs.size()];
        if (isBalanced) {
            for (int i = 0; i < pairs.size(); i++) {
                newMask[i] = pairs.get(i);
            }
        } else {
            logger.info("[Load balancing weight update]{mapCountSize={}, maskSize={}, mapCount={}}",
                    mapCount.size(), pairs.size(), mapCount.entrySet());
            newMask = generateWeightedMask(mapCount);
        }
        return newMask;
    }


    private static <E> E[] generateWeightedMask(Map<E, Integer> weights) {
        boolean isBalanced = weights.values().stream().distinct().count() == 1;
        if (isBalanced) {
            Object[] ret = new Object[weights.size()];
            int i = 0;
            for (E e : weights.keySet()) {
                ret[i] = e;
                i++;
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
            weights.computeIfPresent(selection, (k,v)-> v - 1);
            j++;
            if (j == collect.size()) {
                j = 0;
            }
        }
        return (E[]) newMask;
    }

    public static class Conn implements HasServiceName {

        private final String serviceName;
        private final int id;

        public Conn(String serviceName, int id) {
            this.serviceName = serviceName;
            this.id = id;
        }

        @Override
        public String getServiceName() {
            return serviceName;
        }

        public int getId() {
            return id;
        }

        @Override
        public String toString() {
            return serviceName + id;
        }
    }

    public static class ServiceParam {
        String serviceName;
        int weight;
        int countPod;

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public void setCountPod(int countPod) {
            this.countPod = countPod;
        }

        public ServiceParam() {
        }

        public ServiceParam(String serviceName, int weight, int countPod) {
            this.serviceName = serviceName;
            this.weight = weight;
            this.countPod = countPod;
        }

        public String getServiceName() {
            return serviceName;
        }

        public int getWeight() {
            return weight;
        }

        public int getCountPod() {
            return countPod;
        }
    }
}
