package com.viettel.vocs.microchassis.exception.data_model.message;

import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes.ManagedObjectType;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes.NotificationType;

public class AlarmClearedNotification {
    private String id;
    private NotificationType notificationType;
    private String subscriptionId;
    private long timeStamp;
    private long alarmClearedTime;
    private String alarmId;
    private ManagedObjectType alarmType;

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public AlarmClearedNotification setNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;
        return this;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public AlarmClearedNotification setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
        return this;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public AlarmClearedNotification setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
        return this;
    }

    public long getAlarmClearedTime() {
        return alarmClearedTime;
    }

    public AlarmClearedNotification setAlarmClearedTime(long alarmClearedTime) {
        this.alarmClearedTime = alarmClearedTime;
        return this;
    }

    public String getAlarmId() {
        return alarmId;
    }

    public AlarmClearedNotification setAlarmId(String alarmId) {
        this.alarmId = alarmId;
        return this;
    }

    public ManagedObjectType getAlarmType() {
        return alarmType;
    }

    public AlarmClearedNotification setAlarmType(ManagedObjectType alarmType) {
        this.alarmType = alarmType;
        return this;
    }

    public String getId() {
        return id;
    }

    public AlarmClearedNotification setId(String id) {
        this.id = id;
        return this;
    }
}
