package com.viettel.vocs.microchassis.codec;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author tiennn18
 */
public enum MsgType {
	/**
	 * for any mgr Msg, most has url = msgId if not specific requirements
	 * this affect how client craft mgr msg, and server handle chain filter conditions
	 */
	PING("ping"),
	CREATE("create"),
	DROP("drop"),
	SERVER_STOP("serverStop"),
	UPGRADE("upgrade");
	public static final String CREATE_MSG_ID = "create";
	private static final List<String> allMgrType = Arrays.stream(MsgType.values())
		.map(MsgType::getUrl)
		.collect(Collectors.toList());
	MsgType(String url) {
		this.url = url;
	}

	private final String url;

	public String getUrl() {
		return url;
	}

	public static MsgType fromUrl(String url) {
		for (MsgType event : MsgType.values()) {
			if (event.url.equalsIgnoreCase(url)) {
				return event;
			}
		}
		return null;
	}
}
