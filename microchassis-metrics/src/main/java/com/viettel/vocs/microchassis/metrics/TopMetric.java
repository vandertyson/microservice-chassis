package com.viettel.vocs.microchassis.metrics;

public class TopMetric {
    public double resource;
    public String cpu;
    public double upTime;

    private TopMetric() {
    }

    //%CPU %MEM   RSS TIME
    //229  0.9 75888 00:00:04
    public static TopMetric parse(String inputLine) {
        TopMetric result = new TopMetric();
        String[] split = inputLine.trim().split("\\s+");
        result.cpu = split[0];
        result.resource = Double.parseDouble(split[2]) / 1024D;
        String upString = split[3];
        String[] split1 = upString.split(":");
        long l = Long.parseLong(split1[2]) + Long.parseLong(split1[1]) * 60 + Long.parseLong(split1[0]) * 3600;
        result.upTime = l * 1.0;
        return result;
    }
}
