/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.depricated;

import java.util.concurrent.atomic.AtomicLong;

public class RoundRobinStrategyImpl<E> implements ArraySelectionStrategy<E> {

    private AtomicLong al = new AtomicLong();

    @Override
    public E select(E... selections) {
        long incrementAndGet = al.incrementAndGet();
        if (incrementAndGet >= Long.MAX_VALUE - 1000) {
            al.set(0);
        }
        int id = (int) (incrementAndGet % selections.length);
        return selections[id];
    }

    public void reset() {
        al.set(0);
    }
}
