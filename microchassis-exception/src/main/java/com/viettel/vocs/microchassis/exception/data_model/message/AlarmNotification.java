package com.viettel.vocs.microchassis.exception.data_model.message;

import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes.ManagedObjectType;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes.NotificationType;

public class AlarmNotification {

    private String id;
    private NotificationType notificationType;
    private String subscriptionId;
    private long timeStamp;
    private Alarm alarm;
    private ManagedObjectType alarmType;
    private String type = "AlarmNotification";

    public String getType() {
        return type;
    }

    public AlarmNotification setType(String type) {
        this.type = type;
        return this;
    }

    public String getId() {
        return id;
    }

    public AlarmNotification setId(String id) {
        this.id = id;
        return this;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public AlarmNotification setNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;
        return this;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public AlarmNotification setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
        return this;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public AlarmNotification setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
        return this;
    }

    public Alarm getAlarm() {
        return alarm;
    }

    public AlarmNotification setAlarm(Alarm alarm) {
        this.alarm = alarm;
        return this;
    }

    public ManagedObjectType getAlarmType() {
        return alarmType;
    }

    public AlarmNotification setAlarmType(ManagedObjectType alarmType) {
        this.alarmType = alarmType;
        return this;
    }
}
