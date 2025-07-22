package com.viettel.vocs.mano.model;

public class BlueGreenUpdate {
    public enum Status {
        CONFIRM,
        SUCCESS,
        FAILED
    }

    public enum Type {
        PRE_UPDATE,
        UPDATING,
        UPDATED,
        PRE_UPGRADE,
        UPGRADING,
        UPGRADED
    }

    public String step;
    public Type type;
    public String green_service_name;
    public Integer green_node_port;
    public Integer weight;
    public Status result;
    @Override
    public String toString() {
        return "BlueGreenUpdate{" +
                "step='" + step + '\'' +
                ", type=" + type +
                ", green_service_name='" + green_service_name + '\'' +
                ", green_node_port=" + green_node_port +
                ", weight=" + weight +
                ", result='" + result + '\'' +
                '}';
    }
}
