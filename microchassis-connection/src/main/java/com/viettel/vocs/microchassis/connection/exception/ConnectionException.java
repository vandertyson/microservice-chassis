package com.viettel.vocs.microchassis.connection.exception;

import com.viettel.vocs.microchassis.connection.client.ClientConnection;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ConnectionException extends Exception {
    private final transient ClientConnection sourceClientConnection;

    public enum CONN_ERROR {
        INIT_CONNECT,
        REFUSE_CONNECT,
        NO_CHANNEL,
        // above error need to markAsNotReady
        GENERAL, // unknown error, still be used
        CHANNEL_ERROR, // minor error in runtime, still be used
        TIMEOUT // still be used
    }
    private final Set<CONN_ERROR> types;
    public Set<CONN_ERROR> type(){
        return types;
    }
    public ConnectionException(String message, ClientConnection sourceClientConnection, Set<CONN_ERROR> errorTypes) {
        super(message);
        this.sourceClientConnection = sourceClientConnection; // maybe null if connection select return empty
        if(this.sourceClientConnection != null) {
            label:
            for (CONN_ERROR e : errorTypes) {
                switch (e) {
                    case INIT_CONNECT:
                    case REFUSE_CONNECT:
                    case NO_CHANNEL:
                        this.sourceClientConnection.markAsNotReady();
                        break label;
                }
            }
        }
        types = errorTypes;
    }
    public ConnectionException(String message, ClientConnection sourceClientConnection, CONN_ERROR... errorType) {
        this(message, sourceClientConnection, new HashSet<>(Arrays.asList(errorType)));
    }
    public ConnectionException(String message, ClientConnection sourceClientConnection) {
        this(message, sourceClientConnection, CONN_ERROR.GENERAL);
    }

    public ClientConnection getSourceConnection() {
        return sourceClientConnection;
    }
}
