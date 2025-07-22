package com.viettel.vocs.microchassis.connection;

public interface RequestDuplicator<X> {
    X duplicate(X obj);
}
