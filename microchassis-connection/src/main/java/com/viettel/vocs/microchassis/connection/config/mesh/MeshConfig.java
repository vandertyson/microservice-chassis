package com.viettel.vocs.microchassis.connection.config.mesh;

import java.util.Map;

public abstract class MeshConfig {
	protected final String id;
	public Map<String /*ip*/, Long/*nConn*/> destinations;
	public static final String CONN_MAX = "connection.max";
	public static final String HOST_MIN = "host.min";
	public static final String HOST_MAX = "host.max";
	public static final String CONN_MIN = "connection.min";
	public static final String CLUSTER_SIZE = "cluster";
	public int minConnection = 1;
	public int maxConnection = Integer.MAX_VALUE;
	public Mode mode;
	public abstract static class Mirror {
		public int maxConnection;
		public int minConnection;
//		public Map<Destination, Long> destinations; // with client is connection map, with server is quota to reject mailicious connection or over create
	}
	public String getId() {
		return id;
	}

	public enum Mode { // set at start pod
		FULL_MESH("fullmesh"),
		ASK_LIMIT("ask"),
		CENTER_LIMIT("center");
		private final String label;

		Mode(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}

		public static Mode fromString(String value) {
			for (Mode enumValue : Mode.values()) {
				if (enumValue.label.equalsIgnoreCase(value)) {
					return enumValue;
				}
			}
			return FULL_MESH;
		}
	}
	protected MeshConfig(String id) {
		this(id, Mode.FULL_MESH);
	}
	protected MeshConfig(String id, Mode mode) {
		this.mode = mode;
		this.id = id;
	}
}
