package com.viettel.vocs.microchassis.metrics;

public class PIDStatMetric {
    public String threadName;
    public String cpuID;
    public Double totalCpuConsume;
    public String usrCpuConsume;
    public String sysCpuConsume;


    private PIDStatMetric() {

    }

    //input lines:
    //
    //11:15:43 AM  1001         -     11745    0.00    0.00    0.00    0.00     0  |__GC Thread#2
    // 0       1    2           3       4        5      6        7      8       9     10
    //11:15:43 AM  1001         -     11754    0.00    0.00    0.00    0.00     3  |__Attach Listener
    //11:15:43 AM  1001         -     11772    2.99    7.46    0.00   10.45     2  |__pool-2-thread-1
    //11:15:43 AM  1001         -     11773    4.98    5.47    0.00   10.45     2  |__pool-3-thread-1

    //Average:      UID      TGID       TID    %usr %system  %guest    %CPU   CPU  Command
    //Average:        0         8         -    3.80    0.00    0.00    3.80     -  java
    //Average:        0         -         8    0.00    0.00    0.00    0.00     -  |__java
    // 0              1         2         3       4       5        6       7     8    9
    public static PIDStatMetric parse(String inputLine) {
        PIDStatMetric metric = new PIDStatMetric();
        //get full thread name
        String[] split1 = inputLine.split("\\|_");
        if (split1.length == 1) {
            metric.threadName = split1[0];
        } else {
            metric.threadName = split1[1].substring(1);
        }
        String[] split = split1[0].split("\\s+");
        if (split.length == 10) {
            metric.cpuID = split[9];
            metric.totalCpuConsume = Double.valueOf(split[8].replace(",", "."));
            metric.usrCpuConsume = split[5].replace(",", ".");
            metric.sysCpuConsume = split[6].replace(",", ".");
        } else {
            metric.cpuID = split[8];
            metric.totalCpuConsume = Double.valueOf(split[7].replace(",", "."));
            metric.usrCpuConsume = split[6].replace(",", ".");
            metric.sysCpuConsume = split[5].replace(",", ".");
        }
        return metric;
    }

}
