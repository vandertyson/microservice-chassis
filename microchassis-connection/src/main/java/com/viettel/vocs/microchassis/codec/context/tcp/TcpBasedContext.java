/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.viettel.vocs.microchassis.codec.context.tcp;

import com.viettel.vocs.microchassis.codec.context.ChassisReplyContext;
import com.viettel.vocs.microchassis.tcp.codec.Msg;
import io.netty.channel.ChannelHandlerContext;

public abstract class TcpBasedContext<BiDMsg extends Msg>

	extends ChassisReplyContext<BiDMsg, BiDMsg>
	implements TcpClientContext<BiDMsg>, TcpServerContext<BiDMsg> {


	protected TcpBasedContext(ChannelHandlerContext ctx, BiDMsg request) {
		super(ctx, request);
	}

	protected TcpBasedContext(ChannelHandlerContext ctx, BiDMsg request, int outBufInitSize) {
		super(ctx, request, outBufInitSize);
	}
}
