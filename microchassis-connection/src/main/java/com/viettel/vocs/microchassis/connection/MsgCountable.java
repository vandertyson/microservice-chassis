package com.viettel.vocs.microchassis.connection;

import com.viettel.vocs.common.datatype.DataUnit;

/**
 * @author tiennn18
 */
public interface MsgCountable {
	long getMsgCount();
	double getBytesCount(DataUnit unit);
}
