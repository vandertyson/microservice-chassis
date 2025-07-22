/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.metrics;

/**
 * @author vttek
 */
public enum MetricName {
    INFORMATION_ALL("Information_All"),
    INFORMATION_INIT("Information_Init"),
    INFORMATION_UPDATE("Information_Update"),
    INFORMATION_DELETE("Information_Terminate"),

    Information_Ext("Information_Ext"),
    Information_Internal("Information_Internal"),
    Information_External("Information_External");

    private final String label;

    MetricName(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

}
