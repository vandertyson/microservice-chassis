package com.viettel.vocs.mano.service;

import com.viettel.vocs.mano.model.MetricPushBody;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface MetricPusher {
    CompletableFuture<Boolean> push(Pair<String, List<MetricPushBody>> data);
}
