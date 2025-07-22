package com.viettel.vocs.microchassis.metrics;

import org.apache.commons.lang3.tuple.Pair;

public class CommonLabels {
    private CommonLabels() {
    }

    public enum CommonLabel {
        COMMAND_NAME("command_name"),
        PROTOCOL_NAME("protocol");
        private final String label;

        CommonLabel(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public static Pair<String, String> of(CommonLabel type, String value) {
        return Pair.of(type.getLabel(), value);
    }
}
