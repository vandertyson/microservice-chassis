package com.viettel.vocs.microchassis.connection;

import com.viettel.vocs.common.MathUtils;
import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.hashing.HashFunction;
import com.viettel.vocs.common.hashing.MurmurHash;
import com.viettel.vocs.common.hashing.NodeRouter;
import com.viettel.vocs.common.hashing.RendezvousNodeRouter;
import com.viettel.vocs.common.hashing.WeightedNode;
import com.viettel.vocs.common.hashing.WeightedRendezvousComputer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class RendezvousHashingTest {
    protected static final Logger logger = LogManager.getLogger(RendezvousHashingTest.class);

    @BeforeAll
    public void setUpClass(){
        LogUtils.setupLog4j2();
    }

    private static class Request {

        private final String msisdn;
        private final String type;
        private final String superType;

        public Request(String msisdn, String type, String superType) {
            this.msisdn = msisdn;
            this.type = type;
            this.superType = superType;
        }

        public String getMsisdn() {
            return msisdn;
        }

        public String getType() {
            return type;
        }

        public String getSuperType() {
            return superType;
        }
    }

    @Test
    public void murmurHash() {
        List<WeightedNode> nodes = new ArrayList<>();
        nodes.add(new WeightedNode(1, 2, null));
        nodes.add(new WeightedNode(2, 1, null));
//        nodes.add(new WeightedNode("site3", 1, null));
        final HashFunction hash = MurmurHash::hash64;
        Map<String, AtomicLong> mapCount = new HashMap<>();
        Map<String, Map<String, String>> mapMsi = new HashMap<>();
        NodeRouter<WeightedNode> router = RendezvousNodeRouter.create(nodes, hash, WeightedRendezvousComputer.create());
        long prefix = 847000000000L;
        String[] requestTypes = new String[]{"initialization", "update", "terminate"};
        int maxUpdate = 50;
        int numMsi = 10_000;
        List<Request> listRequest = new ArrayList<>();
        for (int i = 0; i < numMsi; i++) {
            String msisdn = String.valueOf(prefix + i);
            for (String requestType : requestTypes) {
                if ("update".equals(requestType)) {
                    int numUpdate = Math.max(1, MathUtils.randomNextInt(maxUpdate));
                    for (int j = 0; j < numUpdate; j++) {
                        Request r = new Request(msisdn, requestType + j, requestType);
                        listRequest.add(r);
                    }
                } else {
                    Request r = new Request(msisdn, requestType, requestType);
                    listRequest.add(r);
                }
            }
        }
        Collections.shuffle(listRequest);
        Map<String, AtomicLong> countRequestByType = new HashMap<>();
        for (Request request : listRequest) {
            String type = request.getSuperType();
            if (countRequestByType.containsKey(type)) {
                countRequestByType.get(type).incrementAndGet();
            } else {
                countRequestByType.put(type, new AtomicLong(1));
            }
        }
        logger.info("Request by type: {{}} total {}",
                countRequestByType.entrySet().stream().map(f -> f.getKey() + "=" + f.getValue().get()).collect(Collectors.joining(", ")),
                countRequestByType.values().stream().mapToLong(AtomicLong::get).sum());
        long start = System.currentTimeMillis();
        int size = listRequest.size();
        logger.info("================START SEND================");
        int progress = 0;
        for (Request r : listRequest) {
            progress++;
            String msisdn = r.getMsisdn();
            Optional<WeightedNode> node = router.getNode(r.getMsisdn());

            if (node.isPresent()) {
                AtomicLong get = mapCount.get(node.get().getNodeId());

                //count weight
                if (get != null) {
                    get.incrementAndGet();
                } else {
                    mapCount.put(node.get().getNodeId(), new AtomicLong(1));
                }

                //check key
                if (!mapMsi.containsKey(msisdn)) {
                    mapMsi.put(msisdn, new HashMap<>());
                }
                Map<String, String> mapType = mapMsi.get(msisdn);
                mapType.put(r.getType(), node.get().getNodeId());
            } else {
                logger.info("================ERROR================");
            }
//            if (progress % 10000 == 0) {
////                logger.info("Sent " + progress + "/" + size + " request");
//            }
        }
        long end = System.currentTimeMillis();
        logger.info("================CHECK NODE================");
        for (Map.Entry<String, AtomicLong> entry
                : mapCount.entrySet()) {
            logger.info("Node: " + entry.getKey() + "|Count: " + entry.getValue().get());
        }

        logger.info("================CHECK MSISDN================");
        logger.info("Count msi " + mapMsi.size());
        long countFail = 0;
        long countSuccess = 0;
        for (Map.Entry<String, Map<String, String>> entry
                : mapMsi.entrySet()) {
            Collection<String> values = entry.getValue().values();
            long count = values.stream().distinct().count();
            if (count == 1) {
                countSuccess++;
            } else {
                logger.info("Wrong size. msisnd = " + entry.getValue() + ". values=" + entry.getValue().values().stream().collect(Collectors.joining(", ")));
                countFail++;
            }
        }

        logger.info("Count wrong site " + countFail);
        logger.info("Count same site " + countSuccess);
        Iterator<Map.Entry<String, Map<String, String>>> iterator = mapMsi.entrySet().iterator();
        int count = 0;

        while (iterator.hasNext() && count < 10) {
            if (Math.random() > 0.5) {
                Map.Entry<String, Map<String, String>> next = iterator.next();
                logger.info("Success sample. msisdn = " + next.getKey() + ". node=" + next.getValue().entrySet().stream().map(f -> f.getKey() + ":" + f.getValue()).collect(Collectors.joining(", ")));
                count++;
            }
        }
        logger.info("Total time: " + (end - start) + " ms. Total reqest " + size);
        logger.info("Avg tps: " + size * 1000L / (end - start));

    }

}
