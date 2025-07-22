package com.viettel.vocs.microchassis.exception.appender;

public abstract class Appender {

    public abstract boolean sendMessage(String messageID, byte[] data, String endPoint);

    public abstract void close();
}
