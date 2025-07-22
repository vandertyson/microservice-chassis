package com.viettel.vocs.microchassis.codec.context;

import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.GenericFutureListener;

public interface ServerContext<InReq extends ChassisMgrableMsg, CoRes extends ChassisMgrableMsg>
	extends ReplyContext<InReq, CoRes> {
	// server receive msg then parse and hold in ctx as incharged, then create out corresponding reply
	// SERVER ALWAYS REPLY, if drop, it self decompose

	@Override
	CoRes getCoMsg();

	@Override
	InReq getInMsg();
	void sendDrop(GenericFutureListener<ChannelFuture>... futures);
}
