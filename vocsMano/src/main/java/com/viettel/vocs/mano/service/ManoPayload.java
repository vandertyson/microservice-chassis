/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.mano.service;

/**
 *
 * @author vttek
 */
public class ManoPayload {

    public String VNF_INSTANCE_ID;
    public String VNFC_ID;

    public ManoPayload(String VNF_INSTANCE_ID, String VNFC_ID) {
        this.VNF_INSTANCE_ID = VNF_INSTANCE_ID;
        this.VNFC_ID = VNFC_ID;
    }
}
