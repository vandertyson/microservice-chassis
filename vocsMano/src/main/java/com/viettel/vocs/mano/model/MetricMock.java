package com.viettel.vocs.mano.model;

import com.viettel.vocs.microchassis.metrics.MetricCollector;
import com.viettel.vocs.microchassis.metrics.PrometheusMetric;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

interface Contants {
    String NAME = "name";
    String TYPE = "type";
    String OPTIONAL = "optional";
    String VALUE = "value";
}

public interface MetricMock {
    final AtomicLong startTimeMs = new AtomicLong(-1);
    final AtomicLong endTime = new AtomicLong(-1);
    final Map<Integer, Queue<Double>> mapMetricValues = new HashMap<>();

    public static boolean isValid() {
        return endTime.get() > System.currentTimeMillis()
                && mapMetricValues.values().stream()
                .anyMatch(q -> q != null && !q.isEmpty());
    }

    public static boolean mock(String mockData, Logger logger) {
        try {
            if (isValid()) {
                logger.error("[simulateThresholdAlarm] Existing invalid metric mocks");
                return false;
            }
            mapMetricValues.clear();
            //name:type:command:value1,value2;name:type:command:value1,value2
            if (mockData == null || mockData.isEmpty()) {
                logger.error("[simulateThresholdAlarm] Mock data is null or empty");
                return false;
            }

            String[] entries = mockData.split(";");
            List<Object[]> lstMetricNotExist = new ArrayList<>();
            for (String entry : entries) {
                if (entry == null || entry.isEmpty()) return false;

                String[] parts = entry.split("\\|");
                Map<String, String> map = Arrays.stream(parts)
                        .map(part -> part.trim().split("=", 2))
                        .filter(kv -> kv.length == 2)
                        .collect(Collectors.toMap(kv -> kv[0].trim(), kv -> kv[1].trim(), (a, b) -> b));

                // chỉ tách 1 lần đầu tiên

                String name = map.remove(Contants.NAME);
                String type = map.remove(Contants.TYPE);
                String optional = map.remove(Contants.OPTIONAL);
                String value = map.remove(Contants.VALUE);
                if (name == null || name.isEmpty()) {
                    logger.error("[simulateThresholdAlarm] Series name is missing");
                    return false;
                }

                if (value == null || value.isEmpty()) {
                    logger.error("[simulateThresholdAlarm] Series value is missing");
                    return false;
                }


                Queue<Double> values = new LinkedList<>();
                for (String val : value.split(",")) {
                    try {
                        values.add(Double.parseDouble(val.trim()));
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }

                if (values.isEmpty()) return false;
                String key = name + ":" + type + ":" + map;
                logger.info("{}={}", key, values);

                mapMetricValues.put(hash(name, type, map), values);
                if (optional != null && !optional.isEmpty()) {
                    lstMetricNotExist.add(new Object[]{name, type,
                            map.entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue())).collect(Collectors.toList()),
                            optional});
                }
            }
            startTimeMs.set(System.currentTimeMillis());
            endTime.set(startTimeMs.get() + 5 * 60 * 1000);
            lstMetricNotExist.forEach(metric -> {
                if (metric[3].toString().equals("ADD")) {
                    MetricCollector.getMetric().add(metric[0].toString(), (String) metric[1], 0, (List<Pair<String, String>>) metric[2]);
                    logger.info("Incr 0 for {}:{}:{}", metric[0], metric[1], metric[2]);
                } else if (metric[3].toString().equals("SET")) {
                    double lastValue = MetricCollector.getMetric()
                            .getLastValue(metric[0].toString(), (String) metric[1], (List<Pair<String, String>>) metric[2]);
                    if (lastValue == 0.0) {
                        MetricCollector.getMetric()
                                .set(metric[0].toString(), (String) metric[1], 0, (List<Pair<String, String>>) metric[2]);
                        logger.info("Set 0 for {}:{}:{}", metric[0], metric[1], metric[2]);
                    }
                }
            });
            logger.error("[simulateThresholdAlarm] Mock valid in 5 minutes");
            return true;
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            return false;
        }
    }

    private static int hash(String name, String type, Map<String, String> map) {
        return PrometheusMetric.computeHash(name, type, map.entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue())).collect(Collectors.toList()));
    }

    public static Double getNextMetricValue(String name, String type, Map<String, String> map, Double defaultValue, Logger logger) {
        try {
            if (name == null || map == null) {
                return defaultValue;
            }
            map.remove(Contants.TYPE);
            Queue<Double> values = mapMetricValues.get(hash(name, type, map));
            if (values == null || values.isEmpty()) {
                return defaultValue;
            }

            Double res = values.poll();
            logger.info("[getNextMetricValue] name: {}, type: {}, labels: {}, value: {}", name, type, map, res);
            return res; // Lấy và xóa phần tử đầu
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            return defaultValue;
        }
    }
}
