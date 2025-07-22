package com.viettel.vocs.mano.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class MetricPushBodyCompact {
    @Getter @Setter private String hash;
    @Getter @Setter private List<Double> values = new ArrayList<>();
    @Getter @Setter private List<Long> timestamps = new ArrayList<>();
}
