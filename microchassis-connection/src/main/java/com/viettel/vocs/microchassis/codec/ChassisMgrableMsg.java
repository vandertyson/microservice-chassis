package com.viettel.vocs.microchassis.codec;

import com.viettel.vocs.common.IDfy;
import com.viettel.vocs.common.hashing.CheckSumEngine;
import com.viettel.vocs.common.hashing.Checksumable;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import com.viettel.vocs.microchassis.codec.context.ParamHolder;
import com.viettel.vocs.microchassis.codec.handler.Handler;
import com.viettel.vocs.microchassis.connection.server.NettyServer;
import com.viettel.vocs.microchassis.http.codec.HttpMsg;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.Getter;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

interface MsgWithConvertableStringHeader {
	ConcurrentHashMap<String, String> getStringHeaders(); // collect the map
}

interface MsgWithHeader<HeaderType extends Iterable<Map.Entry<HeaderKey, ValueType>>, HeaderKey, ValueType> extends MsgWithConvertableStringHeader {
	HeaderType headers(); // get the HeaderType obj

	/**
	 * return Map raw obj headersz
	 */
	ConcurrentHashMap<HeaderKey, ValueType> getMapHeaders(); // collect the map

	long setDecodeTime(long time);

	long setDecodeTime();

	long getDecodeTime();

	void addHeaders(HeaderType headersObj); // insert or replace keys

	void setHeaders(HeaderType headersObj); // clear all and reset keys

	void onHeader(HeaderKey key, Handler<ValueType> applyer);

	void onHeaderNotNull(HeaderKey key, Handler<ValueType> onHeaderVal);

}


/**
 * @author tiennn18
 * <p>
 * implement theo dung chuan ByteBufHolder cua netty, tat ca cac ban tin deu phai la con cua ByteBufMsg
 */
class ByteBufHolderWrapper {

	/**
	 * param content: khong cho phep expose getter/setter vi co the tao ra orphan ByteBuf chua duoc release
	 */
	private ByteBuf content;

	protected ByteBuf content() {
		if (this instanceof HttpMsg) return content;
		else throw new UnsupportedOperationException();
	}

	public final boolean readOnly; // after replace bb requalify as readOnly or not
	protected final ByteBufAllocator byteBufAllocator;

	protected ByteBufHolderWrapper(ByteBufHolderWrapper origin, boolean share) {
		byteBufAllocator = origin.byteBufAllocator;
		readOnly = origin.readOnly;
		content = share ? origin.content.duplicate() : origin.content.copy();
	}

	protected ByteBufHolderWrapper(ByteBuf buf) {
		content = buf.asReadOnly();
		readOnly = true;
		byteBufAllocator = null;
	}

	public ByteBufHolderWrapper replicate() {
		return new ByteBufHolderWrapper(this, false);
	}

	public ByteBufHolderWrapper project() {
		return new ByteBufHolderWrapper(this, true);
	}

	protected ByteBufHolderWrapper(ByteBufAllocator allocator) {
		byteBufAllocator = allocator;
		readOnly = false;
		content = byteBufAllocator.buffer();
	}

	protected ByteBufHolderWrapper(ByteBufAllocator allocator, int initSize) {
		byteBufAllocator = allocator;
		readOnly = false;
		content = byteBufAllocator.buffer(initSize);
	}

	/**
	 * @deprecated khong can nua vi content la final roi
	 */
	@Deprecated(since = "4.2")
	public void validate() {
		Objects.requireNonNull(content, "Message content is required");
	}

	// WRITE area
	public void replaceContent(Function<ByteBufAllocator, ByteBuf> contentCreator) { // let user add content by this handle, not bring bytebuf around apps, or away
		replaceContent(contentCreator.apply(byteBufAllocator));
	}

	public void replaceContent(ByteBuf newBB) { // let user add content by this handle, not bring bytebuf around apps, or away
		releaseAll(content);
		content = newBB;
	}

	public ByteBufHolderWrapper ensureWritable(int length) {
		content.ensureWritable(length);
		return this;
	}

	public ByteBufHolderWrapper writeNext(ByteBuf appendContent) {
		ensureWritable(appendContent.readableBytes()).content.writeBytes(appendContent);
		return this;
	}

	public ByteBufHolderWrapper writeNext(ByteBuf appendContent, int length) {
		ensureWritable(length).content.writeBytes(appendContent, length);
		return this;
	}

	public ByteBufHolderWrapper writeNext(ByteBuf appendContent, int srcIdx, int length) {
		ensureWritable(length).content.writeBytes(appendContent, srcIdx, length);
		return this;
	}

	public ByteBufHolderWrapper writeNext(byte[] appendContent) {
		ensureWritable(appendContent.length).content.writeBytes(appendContent);
		return this;
	}

	public ByteBufHolderWrapper writeNext(byte[] appendContent, int srcIdx, int length) {
		ensureWritable(length).content.writeBytes(appendContent, srcIdx, length);
		return this;
	}

	public ByteBufHolderWrapper writeFrom(ByteBuf newContent) {
		content.resetWriterIndex();
		return writeNext(newContent);
	}

	public ByteBufHolderWrapper writeFrom(ByteBuf newContent, int length) {
		content.resetWriterIndex();
		return writeNext(newContent, length);
	}

	public ByteBufHolderWrapper writeFrom(ByteBuf newContent, int srcIdx, int length) {
		content.resetWriterIndex();
		return writeNext(newContent, srcIdx, length);
	}

	public ByteBufHolderWrapper writeByUtf8(String newContent) {
		return writeFrom(newContent.getBytes(StandardCharsets.UTF_8));
	}

	public ByteBufHolderWrapper writeByDefaultCharset(String newContent) {
		return writeFrom(newContent.getBytes());
	}

	public ByteBufHolderWrapper writeFrom(byte[] newContent) {
		content.resetWriterIndex();
		return writeNext(newContent);
	}

	public ByteBufHolderWrapper writeFrom(byte[] newContent, int srcIdx, int length) {
		content.resetWriterIndex();
		return writeNext(newContent, srcIdx, length);
	}

	protected static synchronized void releaseAll(ByteBuf content) {
		if (content != null) {
			content.clear();
			while (content.refCnt() >= 1) {
				try {
					content.release();  // can release even on ReadOnlyBB
				} catch (Exception ignored) {
				}
			}
		}
	}


	// MGR area


	// READ AREA

	public ByteBuf getReadOnlyContentByteBuf() {
		return content.asReadOnly();
	}

	/**
	 * Only for netty encode handler
	 *
	 * @param dest netty ByteBuf take care the message
	 */
	public void dumpTo(ByteBuf dest) { // only use this for Encoder
		dest.writeBytes(content);
	}

	//	@Override
	public int refCnt() {
		// alow read info
		return content.refCnt();
	}

	public int readableBytes() {
		return content.readableBytes();
	}

	protected String reportContentBrief() {
		return String.format("{ discardable size=readIdx=%d readableLength=%d writeIdx=%d writable=%d/%d maxWritable=%d/%d}",
			content.readerIndex(), content.readableBytes(), content.writerIndex(), content.writableBytes(), content.capacity(), content.maxWritableBytes(), content.maxCapacity());
	}

	public String toStringUTF8() {
		return toString(StandardCharsets.UTF_8);
	}

	public String toString(Charset charset) {
		return content.toString(charset);
	}

	@Override
	public String toString() {
		return content.toString();
	}

	public synchronized void getContentTo(byte[] dest) { //must sync bcuz reset reader idx
		content.readBytes(dest, 0, Integer.min(dest.length, content.readableBytes()));
		content.resetReaderIndex(); // for other read
	}

	public byte[] getContent() {
		byte[] byteArray = new byte[content.readableBytes()];
		getContentTo(byteArray);
		return byteArray;
	}

	/**
	 * copyFrom method only copy content:
	 * no copy bytebufallocator, which mean move from pool origin to current pool
	 * remove readonly flag
	 * copy content only
	 * any child with same name as this call to this base method
	 *
	 * @param origin
	 */
	public void copyContent(ByteBufHolderWrapper origin) {
		if (origin != null) writeFrom(origin.getContent());
	}

	// Manipulate area
	protected static String byteArrayToHexString(byte[] byteArray) {
		StringBuilder hexString = new StringBuilder(2 * byteArray.length);
		for (byte b : byteArray) {
			String hex = Integer.toHexString(0xFF & b);
			if (hex.length() == 1) {
				// Append a leading zero for single-digit hex values
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}

	protected ByteBufHolderWrapper chassisTouch(Object hint) {
		content.touch(hint);
		return this;
	}

	protected ByteBufHolderWrapper chassisTouch() {
		content.touch();
		return this;
	}

	public synchronized void decompose() {
		try {
			if (this instanceof HttpMsg) ((HttpMsg) this).release(); // release(1) for http2, if no it leak // Http1 only need release 1
			else releaseAll(content);
		} catch (Exception ignored) {
		}
//		headers.clear(); // not affect to copy() headers
	}
}

/**
 * ByteBufMsg is abstract to not allow direct create object from this
 * all child must define copy and duplicate by constructors themselves.
 */
abstract class ByteBufMsg extends ByteBufHolderWrapper implements GenericFutureListener<ChannelFuture> {
	@Override
	public void operationComplete(ChannelFuture f) {
		decompose();
	}

	protected ByteBufMsg(ByteBufAllocator allocator, int initSize) { // create for response
		super(allocator, initSize);
	}

	protected ByteBufMsg(ByteBufAllocator allocator) { // create for response
		super(allocator);
	}

	protected ByteBufMsg(ByteBuf buf) { // decoded from server and client, from some allocator, read only content
		super(buf);
	}

	protected ByteBufMsg(ByteBufMsg origin, boolean shareBuffer) {
		super(origin, shareBuffer); /* share false is copy, deep clone | share true is duplicate */
	}

	public static byte[] toBytes(ByteBuf content) { //must sync bcuz reset reader idx
		byte[] dest = new byte[content.readableBytes()];
		content.readBytes(dest, 0, dest.length);
		content.resetReaderIndex(); // for other read
		return dest;
	}

	public String contentHexString() { // hex
		return byteArrayToHexString(getContent());
	}

	public static void decompose(ByteBuf buf) {
		releaseAll(buf);
	}
}

public abstract class ChassisMgrableMsg<HeaderType extends Iterable<Map.Entry<HeaderKeyType, HeaderValueType>>, HeaderKeyType, HeaderValueType>
	extends ByteBufMsg
	implements Checksumable, MsgWithHeader<HeaderType, HeaderKeyType, HeaderValueType>, ParamHolder {
	public static boolean duplicateFilter(long sentTime) {
		return sentTime != NACK_SENT_TIME; // -1 return false to stop process -> end decodeHandler
	}

	public static int DEFAULT_BUFFER_SIZE = ChassisConfig.ConnectionConfig.HttpConfig.DEFAULT_HTTP_BUFFER_SIZE.getInt();
	protected CheckSumEngine checkSumEngine;

	public static String newMsgId() {
		return IDfy.generateNewId();
	}

	public abstract boolean addServerState(NettyServer server);

	/**
	 * Deprecated : use new Msg(bytebuf).contentHexString() instead if orphan bb to bring bytebuf to netty mgr
	 * // Hien tai moi de o TCP chua dua ve Reply va NoReplyContext
	 * fix 1: neu ByteBuf do app tao ra thi khong can quan tam, ke
	 */
	public static String contentHexString(ByteBuf content) {
		byte[] byteArray = new byte[content.readableBytes()];
		content.readBytes(byteArray);
		content.resetReaderIndex();
		return byteArrayToHexString(byteArray);
	}

	@Override
	public CheckSumEngine getCheckSumEngine() {
		return checkSumEngine;
	}

	public boolean verifyCheckSum() {
		return checkSumEngine == null || checkSumEngine.verify(getReadOnlyContentByteBuf(), getStringHeaders().get(checkSumEngine.getHttpHeaderKey().toString()));
	}

	@Override
	public void setCheckSumEngine(CheckSumEngine engine) {
		checkSumEngine = engine;
	}

	public static final long NACK_SENT_TIME = -1L;
	protected AtomicLong sentTime = new AtomicLong(0L); // default disable monitor timeout by set to 0L
	@Getter
	protected long entrustedStamp = TimeUtils.nowNano();

	public void setEntrustedStamp() {
		this.entrustedStamp = TimeUtils.nowNano();
	}

	public MsgType msgType = null; // null is normal to no need compare

	public boolean isMgr() {
		return msgType != null;
	}

	public void setMgr(MsgType type) {
		msgType = type;
	}

	public boolean isPeerDrop() {
		return MsgType.DROP.equals(msgType) || isServerStop();
	}

	public boolean isPing() {
		return MsgType.PING.equals(msgType);
	}

	public abstract boolean isServerHeaderStop();

	public boolean isServerStop() {
		return MsgType.SERVER_STOP.equals(msgType);
	}

	public boolean isSent() {
		return sentTime.get() > 0;
	}

	public long getSentTime() {
		return sentTime.get();
	}

	public Long sinceSentTime() {
		return sentTime.get() > 0 ? TimeUtils.nowNano() - sentTime.get() : null;
	}

	public void resetSentTime() {
		sentTime.set(0L);
	}

	public void setSentTime(Long newTime) { // only set when haven't set // only use for incoming req, read sentTime from a remember map, then re-set into blank msg
		sentTime.compareAndSet(0, newTime == null ? NACK_SENT_TIME : newTime); // change null to -1 for safe access, -1 which mean duplicate, handled before if this is not a legit server push, this equal 0 in diff time but different in cases handle
	}

	public long setSentTime() {
		sentTime.compareAndSet(0, TimeUtils.nowNano());
		return sentTime.get();
	}

	protected ChassisMgrableMsg(ByteBufAllocator allocator, int initSize) {
		super(allocator, initSize);
	}

	public <T extends ChassisMgrableMsg<HeaderType, HeaderKeyType, HeaderValueType>> void copyAll(T oriMsg) {
		super.copyContent(oriMsg);
		sentTime.set(oriMsg.sentTime.get());
		setMessageId(oriMsg.getMessageId());
		setUrl(oriMsg.getUrl());
		setDecodeTime(oriMsg.getDecodeTime());
	}

	protected ChassisMgrableMsg(ByteBufAllocator allocator) {
		super(allocator);
	}

	protected ChassisMgrableMsg(ByteBuf buf) { // for readonly
		super(buf);
	}

	protected ChassisMgrableMsg(ChassisMgrableMsg<HeaderType, HeaderKeyType, HeaderValueType> origin, boolean shareBuffer) {
		super(origin, shareBuffer);
	}

	public long setDecodeTime() {
		return setDecodeTime(TimeUtils.nowNano());
	}

	public ConcurrentHashMap<HeaderKeyType, HeaderValueType> getMapHeaders() { // default for msg types that not have concurrent map based header type
		ConcurrentHashMap<HeaderKeyType, HeaderValueType> result = new ConcurrentHashMap<>();
		headers().forEach(header -> result.put(header.getKey(), header.getValue()));
		return result;
	}

	public abstract String getMessageId();

	public abstract ChassisMgrableMsg<HeaderType, HeaderKeyType, HeaderValueType> setMessageId(String messageId);

	public abstract String getUrl();

	public abstract ChassisMgrableMsg<HeaderType, HeaderKeyType, HeaderValueType> setUrl(String newUrl);

	public abstract String getProtoVersionText();

	QueryStringDecoder qsd = null;

	public final Map<String, List<String>> getInParams() {
		return qsd == null ? (qsd = new QueryStringDecoder(getUrl())).parameters() : qsd.parameters();
	}
}

