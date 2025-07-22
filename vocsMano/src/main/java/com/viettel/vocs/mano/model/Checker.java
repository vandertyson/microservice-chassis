package com.viettel.vocs.mano.model;

import com.viettel.vocs.microchassis.base.ChassisConfig;

public interface Checker<T> {
    String getCheckListID();

    Checkable<T> performCheck();

    default int getCheckTimeoutSec() {

        return ChassisConfig.HealthcheckConfig.HEALTHCHECK_TIMEOUT_SEC.getInt();
    }
}