package com.viettel.vocs.microchassis.exception.data_model.message;

import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes.AckState;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes.EventType;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes.FaultType;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes.PerceivedSeverityType;

import java.util.List;
import java.util.Map;

public class Alarm {
    private String id;
    private String managedObjectId;
    private String managedObjectName;
    private List<String> vnfcIds;
    private List<String> vnfcNames;
    private Long alarmRaisedTime;
    private Long alarmChangedTime;
    private Long alarmClearedTime;
    private AckState ackState;
    private PerceivedSeverityType perceivedSeverity;
    private long eventTime;
    private EventType eventType;
    private FaultType faultType;
    private String probableCause;
    private boolean isRootCause;
    private List<String> correlatedAlarmIds;
    private List<String> faultDetails;
    private Map<String, String> metadata;

    public String getId() {
        return id;
    }

    public Alarm setId(String id) {
        this.id = id;
        return this;
    }

    public String getManagedObjectId() {
        return managedObjectId;
    }

    public Alarm setManagedObjectId(String managedObjectId) {
        this.managedObjectId = managedObjectId;
        return this;
    }

    public String getManagedObjectName() {
        return managedObjectName;
    }

    public Alarm setManagedObjectName(String managedObjectName) {
        this.managedObjectName = managedObjectName;
        return this;
    }

    public List<String> getVnfcIds() {
        return vnfcIds;
    }

    public Alarm setVnfcIds(List<String> vnfcIds) {
        this.vnfcIds = vnfcIds;
        return this;
    }

    public List<String> getVnfcNames() {
        return vnfcNames;
    }

    public Alarm setVnfcNames(List<String> vnfcNames) {
        this.vnfcNames = vnfcNames;
        return this;
    }

    public long getAlarmRaisedTime() {
        return alarmRaisedTime;
    }

    public Alarm setAlarmRaisedTime(Long alarmRaisedTime) {
        this.alarmRaisedTime = alarmRaisedTime;
        return this;
    }

    public long getAlarmChangedTime() {
        return alarmChangedTime;
    }

    public Alarm setAlarmChangedTime(long alarmChangedTime) {
        this.alarmChangedTime = alarmChangedTime;
        return this;
    }

    public long getAlarmClearedTime() {
        return alarmClearedTime;
    }

    public Alarm setAlarmClearedTime(Long alarmClearedTime) {
        this.alarmClearedTime = alarmClearedTime;
        return this;
    }

    public AckState getAckState() {
        return ackState;
    }

    public Alarm setAckState(AckState ackState) {
        this.ackState = ackState;
        return this;
    }

    public PerceivedSeverityType getPerceivedSeverity() {
        return perceivedSeverity;
    }

    public Alarm setPerceivedSeverity(PerceivedSeverityType perceivedSeverity) {
        this.perceivedSeverity = perceivedSeverity;
        return this;
    }

    public long getEventTime() {
        return eventTime;
    }

    public Alarm setEventTime(long eventTime) {
        this.eventTime = eventTime;
        return this;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Alarm setEventType(EventType eventType) {
        this.eventType = eventType;
        return this;
    }

    public FaultType getFaultType() {
        return faultType;
    }

    public Alarm setFaultType(FaultType faultType) {
        this.faultType = faultType;
        return this;
    }

    public String getProbableCause() {
        return probableCause;
    }

    public Alarm setProbableCause(String probableCause) {
        this.probableCause = probableCause;
        return this;
    }

    public boolean isRootCause() {
        return isRootCause;
    }

    public Alarm setRootCause(boolean rootCause) {
        isRootCause = rootCause;
        return this;
    }

    public List<String> getCorrelatedAlarmIds() {
        return correlatedAlarmIds;
    }

    public Alarm setCorrelatedAlarmIds(List<String> correlatedAlarmIds) {
        this.correlatedAlarmIds = correlatedAlarmIds;
        return this;
    }

    public List<String> getFaultDetails() {
        return faultDetails;
    }

    public Alarm setFaultDetails(List<String> faultDetails) {
        this.faultDetails = faultDetails;
        return this;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }   
}
