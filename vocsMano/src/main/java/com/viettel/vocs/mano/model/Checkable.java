package com.viettel.vocs.mano.model;

public interface Checkable<E> {
    boolean isHealthy();

    E getCheckDetail();
}