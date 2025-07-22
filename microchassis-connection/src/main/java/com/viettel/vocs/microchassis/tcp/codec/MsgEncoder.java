package com.viettel.vocs.microchassis.tcp.codec;

import com.viettel.vocs.microchassis.base.ChassisConst;
import com.viettel.vocs.microchassis.connection.config.ChannelConfiguration;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MsgEncoder extends MessageToByteEncoder<Msg> {
	private static final Logger logger = LogManager.getLogger(MsgEncoder.class);
	private ChannelConfiguration config;


	public ChannelConfiguration getConfig() {
		return config;
	}

	public void setConfig(ChannelConfiguration config) {
		this.config = config;
	}

	public MsgEncoder(ChannelConfiguration config, String peerId) {
		this.config = config;
		this.peerId = peerId;
	}

	public MsgEncoder(Class<? extends Msg> outboundMessageType, ChannelConfiguration config, String peerId) {
		super(outboundMessageType);
		this.config = config;
		this.peerId = peerId;
	}

	public MsgEncoder(boolean preferDirect, String id) {
		super(preferDirect);
		this.peerId = id;
	}

	public MsgEncoder(Class<? extends Msg> outboundMessageType, boolean preferDirect, String id) {
		super(outboundMessageType, preferDirect);
		this.peerId = id;
	}

	private final String peerId;

	@Override
	protected void encode(ChannelHandlerContext chc, Msg msg, ByteBuf bb) throws Exception {
		try {
			msg.headers.set(MsgHeader.LOAD_HEADER, String.valueOf(ChassisConst.cpuMon/*do not change to interface method*/.instanceCpuUtilization.get()));
			if (logger.isDebugEnabled()) {
				logger.debug("Encode outbound msg. id={}, ctx={}, orginMsg={}, headers={}, beforeReadableBytes={}}",
					peerId, chc.channel(), msg, msg.getStringHeaders(), bb.readableBytes());
			}
			msg.encodeTo(bb);
			msg.decompose(); // TODO qualify this: do app need to refer to this msg after send?
		} catch (Throwable ex) {
			logger.error(ex, ex);
		}
	}

}
