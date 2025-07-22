package com.viettel.vocs.microchassis.connection.event;

import com.viettel.vocs.microchassis.codec.context.CtxHolder;
import io.netty.channel.ChannelHandlerContext;
import org.jetbrains.annotations.NotNull;

public class ContextEvent extends InternalEvent implements CtxHolder {
	private final ChannelHandlerContext ctx;
	public ContextEvent(EventType type, @NotNull ChannelHandlerContext ctx, String message) {
		super(type, message);
		this.ctx = ctx;
	}
	public ContextEvent(EventType type, @NotNull ChannelHandlerContext ctx) {
		super(type);
		this.ctx = ctx;
	}

	@Override
	public EventType getType() {
		return (EventType) type;
	}
	@Override
	public ChannelHandlerContext getCtx() {
		return ctx;
	}
	@Override
	public String toString() {
		return String.format("%s{type=%s, context=%s, channel=%s}", getClass().getSimpleName(), getType(), getCtx(), getChannelString());
	}

	public enum EventType implements EventCatalog {
		CHANNEL_ACTIVE(100),
		CHANNEL_INACTIVE(101),
		SERVER_DOWN(103),
		MAX_CONCURRENT_REACH(105),
		PING(107),
		CONTEXT_REMOVED(108);
//	eventHandler.addEventHandler(ContextEvent.EventType.CONTEXT_REMOVED, evt -> {
//			for (Iterator<Map.Entry<ApplicationID, ChannelHandlerContext>> it = mapChannel.entrySet().iterator(); it.hasNext(); ) {
//				Map.Entry<ApplicationID, ChannelHandlerContext> entry = it.next();
//				if (ctx.channel().id().asLongText().equals(entry.getValue().channel().id().asLongText())) {
//					mapChannel.remove(entry.getKey());
//					logger.info("Remove channel {} from map. Map size {}", ctx.channel(), mapChannel.size());
//					return;
//				}
//			}
//		});
		private final int code;

		EventType(int eventCode) {
			this.code = eventCode;
		}
		@Override
		public int getCode() {
			return code;
		}
	}
}
