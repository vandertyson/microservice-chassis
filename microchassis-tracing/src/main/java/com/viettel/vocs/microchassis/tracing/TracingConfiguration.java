package com.viettel.vocs.microchassis.tracing;

import com.viettel.vocs.common.config.tracing.JaegerConfiguration;
import com.viettel.vocs.common.file.YamlUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;

import java.util.HashSet;
import java.util.Set;

public class TracingConfiguration {
    private boolean enableTracing = false;
    private Set<String> listMsisdn;
    private Set<String> listSessionId;
    private JaegerConfiguration jaegerConfiguration;
    private long maxTracingIdleSec = ChassisConfig.TracingConfig.MAX_TRACING_IDLE_SEC.get();

    public TracingConfiguration() {
        this.listMsisdn = new HashSet<>();
        this.listSessionId = new HashSet<>();
    }

    public boolean isEnableTracing() {
        return enableTracing;
    }

    public void setEnableTracing(boolean enableTracing) {
        this.enableTracing = enableTracing;
    }

    public Set<String> getListMsisdn() {
        return listMsisdn;
    }

    public void setListMsisdn(Set<String> listMsisdn) {
        this.listMsisdn = listMsisdn;
    }

    public Set<String> getListSessionId() {
        return listSessionId;
    }

    public void setListSessionId(Set<String> listSessionId) {
        this.listSessionId = listSessionId;
    }

    public JaegerConfiguration getJaegerConfiguration() {
        return jaegerConfiguration;
    }

    public void setJaegerConfiguration(JaegerConfiguration jaegerConfiguration) {
        this.jaegerConfiguration = jaegerConfiguration;
    }

    public static void main(String[] args) {
        try {
            TracingConfiguration config = new TracingConfiguration();
            JaegerConfiguration jc = new JaegerConfiguration();
            jc.jaegerAgentPort = "4301";
            config.setJaegerConfiguration(jc);
            //
            String file = "/home/vttek/tracing.yml";
            YamlUtils.objectToYamlFile(config, file);
            TracingConfiguration parsed = YamlUtils.objectFromYaml(TracingConfiguration.class, file);
            System.out.print(parsed.getJaegerConfiguration().jaegerAgentPort);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public Long getMaxTracingIdleSec() {
        return maxTracingIdleSec;
    }

    public void setMaxTracingIdleSec(Long maxTracingIdleSec) {
        this.maxTracingIdleSec = maxTracingIdleSec;
    }

    @Override
    public String toString() {
        return "{ listMsisdn=" + listMsisdn +
                ", listSessionId=" + listSessionId +
                ", maxTracingIdleSec=" + maxTracingIdleSec +
                '}';
    }
}
