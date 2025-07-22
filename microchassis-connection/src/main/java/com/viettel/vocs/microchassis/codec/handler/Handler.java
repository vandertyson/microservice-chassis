/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.codec.handler;

/**
 *
 * @author vttek
 */
public interface Handler<HandleArgType> {

	/**
     * Handle everything, kinda applier
     * @param arg
     */
    void handle(HandleArgType arg);
}