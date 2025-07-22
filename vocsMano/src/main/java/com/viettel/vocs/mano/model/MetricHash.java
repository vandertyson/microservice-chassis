package com.viettel.vocs.mano.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

public class MetricHash {
    @Getter
    @Setter
    private String status;
    @Getter
    @Setter
    private Map<String, String> data;
}
