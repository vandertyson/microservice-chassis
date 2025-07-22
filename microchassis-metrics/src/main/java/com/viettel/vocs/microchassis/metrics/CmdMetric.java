package com.viettel.vocs.microchassis.metrics;

import com.viettel.vocs.common.CommonConst;
import com.viettel.vocs.microchassis.base.ChassisConst;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

class CmdMetric {

    public static long getCurrentPid() {
        return CommonConst.SystemConst.PID;
    }

    public static List<String> getCPU() throws IOException {
        List<String> result = new ArrayList<>();

        Runtime r = Runtime.getRuntime();

        Process p = r.exec(ChassisConst.pidStatCommand);

        BufferedReader in =
                new BufferedReader(new InputStreamReader(p.getInputStream()));
        String inputLine;
        int header = 0;
        while ((inputLine = in.readLine()) != null) {
            if (inputLine.contains("CPU")) {
                header++;
            } else {
                result.add(inputLine);
            }
            if (header == 3) {
                break;
            }
        }
        in.close();
        p.destroyForcibly();
        return result;
    }

}
