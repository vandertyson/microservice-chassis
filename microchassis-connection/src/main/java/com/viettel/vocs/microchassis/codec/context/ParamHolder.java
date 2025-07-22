package com.viettel.vocs.microchassis.codec.context;

import java.util.List;
import java.util.Map;

/**
 * thanh phan co ban cua 1 msg la
 * 1. data: data/content/body
 * 2. msgId: msg phai duoc dinh danh neu co body trung nhau
 * 3. sentTime: msg ghi lai thoi gian duoc gui di tu peer cuoi cung, co the theo doi ke ca internal msg
 * => cac api tuong ung cho MsgHolder can phai co lien quan den 3 thanh phan nay
 */
public interface ParamHolder {
	Map<String, List<String>> getInParams();
}
