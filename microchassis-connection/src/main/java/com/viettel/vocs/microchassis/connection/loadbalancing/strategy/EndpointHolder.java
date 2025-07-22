package com.viettel.vocs.microchassis.connection.loadbalancing.strategy;

import com.viettel.vocs.microchassis.base.Endpoint;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

public interface EndpointHolder {
	Endpoint getEndpoint();

	static <T extends EndpointHolder> long countEndpoint(Collection<T> list, Endpoint target) {
		return list.stream().filter(conn -> Objects.equals(conn.getEndpoint(), target)).count();
	}

	static <T extends EndpointHolder> Stream<T> filterEndpoint(Collection<T> list, Endpoint target) {
		return list.stream().filter(conn -> Objects.equals(conn.getEndpoint(), target));
	}
}
