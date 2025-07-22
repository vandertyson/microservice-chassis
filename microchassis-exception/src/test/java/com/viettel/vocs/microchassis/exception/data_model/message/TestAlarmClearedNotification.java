package com.viettel.vocs.microchassis.exception.data_model.message;

import com.viettel.vocs.common.file.JsonUtils;
import com.viettel.vocs.common.IDfy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestAlarmClearedNotification {
    @Test
    public void testAlarmClearedNotification() {
        String id = IDfy.generateNewId();
        String alarmId = IDfy.generateNewId();

        AlarmClearedBuilder builder = new AlarmClearedBuilder()
                .withId(id)
                .withAlarmId(alarmId);

        AlarmClearedNotification alarmClearedNotification = builder.build();
        System.out.println(JsonUtils.getEncoder().toJson(alarmClearedNotification));

        Assertions.assertEquals(id, alarmClearedNotification.getId());
        Assertions.assertEquals(alarmId, alarmClearedNotification.getAlarmId());
    }
}
