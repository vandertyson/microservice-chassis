package com.viettel.vocs.microchassis.codec.context;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import com.viettel.vocs.microchassis.codec.MsgType;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.GenericFutureListener;

public interface MsgHolder<InChargeMsg extends ChassisMgrableMsg>
	extends GenericFutureListener<ChannelFuture>, ParamHolder {
	void decompose(); // all trigger by PeerConfig.channelConfiguration.decomposeCtxOnDone
	InChargeMsg getInMsg();
	boolean isInMgr();
	MsgType getInMsgType();

//	void retainInMsg(); // copy msg to other object, then reassign into incoming msg to let decoder not release msg after handle

	String getInPath(); // same among req/res/multiRes
	String getInID();

	byte[] getInData();

}
