package com.viettel.vocs.microchassis.exception.data_model.message;

import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.common.IDfy;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class TestAlarmNotification {
    @Test
    public void testAlarmNotification1() {
        String id = IDfy.generateNewId();
        String managedObjectId = "d4838036-e7dc-4b00-84be-47fd2f2c2584";
        String managedObjectName = "vnfm-63e3e6e62cad";
        long alarmRaisedTime = System.currentTimeMillis();
        AckState ackState = AckState.UNACKNOWLEDGED;
        PerceivedSeverityType perceivedSeverity = PerceivedSeverityType.CRITICAL;
        long eventTime = System.currentTimeMillis();
        EventType eventType = EventType.COMMUNICATIONS_ALARM;
        String probableCause = "Vnfc with Id 05225fe3-2b08-4971-80d8-eff992f246e2 is ERROR";
        boolean isRootCause = true;
        FaultType faultType = FaultType.VNFC_INSTANCE;

        Map<String, String> metaData = new HashMap<>();
        metaData.put("vnfInstanceName", "Tuyenlt6");

//        try {
        AlarmEventBuilder builder = new AlarmEventBuilder()
                .withId(id)
                .withManagedObjectId(managedObjectId)
                .withManagedObjectName(managedObjectName)
                .withAckState(ackState)
                .withPerceivedSeverity(perceivedSeverity)
                .withEventTime(eventTime)
                .withEventType(eventType)
                .withProbableCause(probableCause)
                .withIsRootCause(isRootCause)
                .withFaultType(faultType)
                .withAllMetadata(metaData)
                .withNotificationType(NotificationType.ALARM_NOTIFICATION)
                .withAlarmType(ManagedObjectType.VNF_OBJECT);
        AlarmNotification alarmNotification = builder.build();
        System.out.println(JsonUtils.getEncoder().toJson(alarmNotification));

        Assertions.assertEquals(id, alarmNotification.getId());
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }
}
