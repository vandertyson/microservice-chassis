package com.viettel.vocs.mano.model;

import lombok.Getter;
import lombok.Setter;

public class Report {
    @Getter @Setter CheckType type;
    @Getter @Setter String checkID;
    @Getter @Setter String startTime;
    @Getter @Setter Long duration;
    @Getter @Setter boolean healthy;
    @Getter @Setter Object detail;
}