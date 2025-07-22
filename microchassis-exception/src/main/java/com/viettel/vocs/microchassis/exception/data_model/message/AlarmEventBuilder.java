package com.viettel.vocs.microchassis.exception.data_model.message;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.IDfy;
import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes.*;
import com.viettel.vocs.microchassis.exception.data_model.type.AlarmParameter;
import com.viettel.vocs.microchassis.exception.utils.ParserUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlarmEventBuilder implements MessageBuilder {

    private String id = IDfy.generateNewId();
    private String managedObjectId = "";
    private String managedObjectName = "";
    private List<String> vnfcIds;
    private List<String> vnfcNames;
    private AckState ackState = AckState.UNACKNOWLEDGED;
    private PerceivedSeverityType perceivedSeverity = PerceivedSeverityType.WARNING;
    private long eventTime = System.currentTimeMillis();
    private EventType eventType = EventType.PROCESSING_ERROR_ALARM;
    private FaultType faultType = FaultType.VNF_INSTANCE;
    private String probableCause = AlarmParameter.EXCEPTION;
    private boolean isRootCause = false;
    private List<String> faultDetails = null;
    private Map<String, String> metaData = null;
    private Throwable throwable;

    private NotificationType notificationType = NotificationType.ALARM_NOTIFICATION;
    private String subscriptionId = "";
    private ManagedObjectType alarmType = ManagedObjectType.VNF_OBJECT;
    private String type;
    private Long alarmClearedTime;
    private Long alarmRaisedTime;

    public Long getAlarmRaisedTime() {
        return alarmRaisedTime;
    }

    public AlarmEventBuilder withAlarmRaisedTime(Long alarmRaisedTime) {
        this.alarmRaisedTime = alarmRaisedTime;
        return this;
    }

    public Long getAlarmClearedTime() {
        return alarmClearedTime;
    }

    public AlarmEventBuilder withAlarmClearedTime(Long alarmClearedTime) {
        this.alarmClearedTime = alarmClearedTime;
        return this;
    }

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

    public AlarmEventBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public AlarmEventBuilder withManagedObjectId(String managedObjectId) {
        this.managedObjectId = managedObjectId;
        return this;
    }

    public AlarmEventBuilder withManagedObjectName(String managedObjectName) {
        this.managedObjectName = managedObjectName;
        return this;
    }

    public AlarmEventBuilder withVnfcIds(List<String> vnfcIds) {
        this.vnfcIds = vnfcIds;
        return this;
    }

    public AlarmEventBuilder withVnfcNames(List<String> vnfcName) {
        this.vnfcNames = vnfcName;
        return this;
    }

    public AlarmEventBuilder withAckState(AckState ackState) {
        this.ackState = ackState;
        return this;
    }

    public AlarmEventBuilder withPerceivedSeverity(PerceivedSeverityType perceivedSeverity) {
        this.perceivedSeverity = perceivedSeverity;
        return this;
    }

    public AlarmEventBuilder withEventTime(long eventTime) {
        this.eventTime = eventTime;
        return this;
    }

    public AlarmEventBuilder withEventType(EventType eventType) {
        this.eventType = eventType;
        return this;
    }

    public AlarmEventBuilder withFaultType(FaultType faultType) {
        this.faultType = faultType;
        return this;
    }

    public AlarmEventBuilder withProbableCause(String probableCause) {
        this.probableCause = probableCause;
        return this;
    }

    public AlarmEventBuilder withIsRootCause(boolean isRootCause) {
        this.isRootCause = isRootCause;
        return this;
    }

    public AlarmEventBuilder withFaultDetails(String faultDetail) {
        if (this.faultDetails == null) {
            this.faultDetails = new ArrayList<>();
        }

        this.faultDetails.add(faultDetail);
        return this;
    }

    public AlarmEventBuilder withAllFaultDetails(List<String> faultDetails) {
        this.faultDetails = faultDetails;
        return this;
    }

    public AlarmEventBuilder withMetadata(String key, String value) {
        if (this.metaData == null) {
            this.metaData = new HashMap<>();
        }

        this.metaData.put(key, value);
        return this;
    }

    public AlarmEventBuilder withAllMetadata(Map<String, String> metaData) {
        this.metaData = metaData;
        return this;
    }

    public AlarmEventBuilder withThrowable(Throwable throwable) {
        this.throwable = throwable;
        return this;
    }

    public AlarmEventBuilder withNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;
        return this;
    }

    public AlarmEventBuilder withSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
        return this;
    }

    public AlarmEventBuilder withAlarmType(ManagedObjectType alarmType) {
        this.alarmType = alarmType;
        return this;
    }

    public AlarmEventBuilder withType(String type) {
        this.type = type;
        return this;
    }

    public AlarmNotification build() {
        this.withMetadata(AlarmParameter.HOST_IP, CommonConfig.InstanceInfo.HOST_IP.get());
        this.withMetadata(AlarmParameter.HOST_NAME, ConnectionManager.getMyDNS());
        this.withMetadata(AlarmParameter.SERVICE_NAME, CommonConfig.InstanceInfo.VDU_NAME.get());

        if (this.throwable != null) {
            String stackTrace = ParserUtils.getStackTrace(this.throwable);
            this.withFaultDetails("message: " + this.throwable.getMessage());
            this.withFaultDetails("stackTrace: " + stackTrace);

            if (this.probableCause == null) {
                this.withProbableCause(stackTrace);
            }
        }

        String idAlarm = IDfy.generateNewId();
        Alarm alarm = new Alarm()
                .setId(idAlarm)
                .setVnfcIds(vnfcIds)
                .setVnfcNames(vnfcNames)
                .setManagedObjectId(this.managedObjectId)
                .setManagedObjectName(this.managedObjectName)
                .setAckState(this.ackState)
                .setPerceivedSeverity(this.perceivedSeverity)
                .setEventTime(this.eventTime)
                .setEventType(this.eventType)
                .setFaultType(this.faultType)
                .setProbableCause(this.probableCause)
                .setAlarmClearedTime(this.alarmClearedTime)
                .setAlarmRaisedTime(alarmRaisedTime)
                .setRootCause(this.isRootCause);
        if (this.faultDetails != null) {
            alarm.setFaultDetails(this.faultDetails);
        }
        if (this.metaData != null) {
            alarm.setMetadata(this.metaData);
        }

        AlarmNotification alarmNotification = new AlarmNotification()
                .setId(this.id)
                .setNotificationType(this.notificationType)
                .setSubscriptionId(this.subscriptionId)
                .setTimeStamp(this.eventTime)
                .setAlarm(alarm)
                .setAlarmType(this.alarmType)
                .setType(type);

        return alarmNotification;
    }
}
