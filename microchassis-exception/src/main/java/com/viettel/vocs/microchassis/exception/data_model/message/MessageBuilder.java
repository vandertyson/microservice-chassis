package com.viettel.vocs.microchassis.exception.data_model.message;

public interface MessageBuilder {
    String getId();
    byte[] getBytesOfJson();
    AlarmTypes.NotificationType getNotificationType();
}
