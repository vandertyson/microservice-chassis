package com.viettel.vocs.microchassis.exception.data_model.message;

public class AlarmTypes {
    public enum AckState {
        UNACKNOWLEDGED,
        ACKNOWLEDGED
    }

    public enum PerceivedSeverityType {
        CRITICAL,
        MAJOR,
        MINOR,
        WARNING,
        INDETERMINATE,
        CLEARED
    }

    public enum EventType {
        COMMUNICATIONS_ALARM,
        PROCESSING_ERROR_ALARM,
        ENVIROMENTAL_ALARM,
        QOS_ALARM,
        EQUIPMENT_ALARM
    }

    public enum FaultType {
        VNF_INSTANCE,
        VNFC_INSTANCE
    }

    public enum NotificationType {
        ALARM_NOTIFICATION,
        ALARM_CLEARED_NOTIFICATION
    }

    public enum ManagedObjectType {
        VNF_OBJECT,
        CNF_OBJECT
    }
}
