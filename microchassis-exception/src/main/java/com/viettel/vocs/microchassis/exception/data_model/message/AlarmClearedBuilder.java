package com.viettel.vocs.microchassis.exception.data_model.message;

import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.common.IDfy;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes.ManagedObjectType;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes.NotificationType;

import java.nio.charset.StandardCharsets;

public class AlarmClearedBuilder implements MessageBuilder {
    private String id = IDfy.generateNewId();
    private NotificationType notificationType = NotificationType.ALARM_CLEARED_NOTIFICATION;
    private String subscriptionId = "";
    private long timeStamp = System.currentTimeMillis();
    private long alarmClearedTime = System.currentTimeMillis();
    private String alarmId;
    private ManagedObjectType alarmType = ManagedObjectType.VNF_OBJECT;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public byte[] getBytesOfJson() {
        return JsonUtils.getEncoder().toJson(this.build()).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public NotificationType getNotificationType() {
        return notificationType;
    }

    public AlarmClearedBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public AlarmClearedBuilder withNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;
        return this;
    }

    public AlarmClearedBuilder withSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
        return this;
    }

    public AlarmClearedBuilder withTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
        return this;
    }

    public AlarmClearedBuilder withClearedTime(long alarmClearedTime) {
        this.alarmClearedTime = alarmClearedTime;
        return this;
    }

    public AlarmClearedBuilder withAlarmId(String alarmId) {
        this.alarmId = alarmId;
        return this;
    }

    public AlarmClearedBuilder withAlarmType(ManagedObjectType alarmType) {
        this.alarmType = alarmType;
        return this;
    }

    public AlarmClearedNotification build() {
        AlarmClearedNotification alarmClearedNotification = new AlarmClearedNotification()
                .setId(this.id)
                .setNotificationType(this.notificationType)
                .setSubscriptionId(this.subscriptionId)
                .setTimeStamp(this.timeStamp)
                .setAlarmClearedTime(this.alarmClearedTime)
                .setAlarmId(this.alarmId)
                .setAlarmType(this.alarmType);

        return alarmClearedNotification;
    }
}
