/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.depricated;

/**
 *
 * @author vttek
 */
public interface ArraySelectionStrategy<E> {
    E select(E... selections);
}
