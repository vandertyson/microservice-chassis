package com.viettel.vocs.microchassis.connection.exception;

import com.viettel.vocs.microchassis.connection.client.ClientConnection;

import java.util.Set;

public class SendTimeout extends ConnectionException {

	public SendTimeout(String message, ClientConnection sourceClientConnection, CONN_ERROR... errorTypes) {
		super(message, sourceClientConnection, errorTypes);
	}

	public SendTimeout(String message, ClientConnection sourceClientConnection, Set<CONN_ERROR> errorTypes) {
		super(message, sourceClientConnection, errorTypes);
	}
}
