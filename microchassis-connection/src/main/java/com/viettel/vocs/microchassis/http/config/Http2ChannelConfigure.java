package com.viettel.vocs.microchassis.http.config;

import com.viettel.vocs.common.config.loader.ConfigLoader;
import com.viettel.vocs.microchassis.base.ChassisConfig;
import io.netty.handler.codec.http2.Http2Settings;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_LIST_SIZE;

public class Http2ChannelConfigure extends ConfigLoader<Http2ChannelConfigure> {
	public int initialCapacity = ChassisConfig.ConnectionConfig.Http2Config.HTTP2_INITIAL_CAPACITY.getInt();
	public int http2AwaitSettingSeconds = ChassisConfig.ConnectionConfig.Http2Config.HTTP2_AWAITSETTINGSECONDS.getInt();
	public long maxConcurrentStream = ChassisConfig.ConnectionConfig.Http2Config.MAX_CONCURRENTSTREAM.get();
	public long maxHeaderListSize = ChassisConfig.ConnectionConfig.Http2Config.HTTP2_MAX_HEADERLISTSIZE.get();
	public int initialWindowSize = ChassisConfig.ConnectionConfig.Http2Config.HTTP2_INITIAL_WINDOWSIZE.getInt();
	public boolean encoderEnforceMaxConcurrentStreams = ChassisConfig.ConnectionConfig.Http2Config.ENCODER_ENFORCEMAXCONCURRENTSTREAMS.get();
	public static int resetStreamIdThreshold = 2_000_000_000;
	public boolean isUpgradeHttp2 = ChassisConfig.ConnectionConfig.Http2Config.IS_UPGRADEHTTP2.get();
	public boolean ignoreUpgradeHttp2 = ChassisConfig.ConnectionConfig.Http2Config.IGNORE_UPGRADEHTTP2.get();
	public long pingTimeoutMs = ChassisConfig.ConnectionConfig.PINGTIMEOUT_MS.get();
	public int maxContentLength = ChassisConfig.ConnectionConfig.Http2Config.HTTP2_MAXCONTENT_LENGTH.getInt();
	public Http2Settings makeSetting(){
		return new Http2Settings(initialCapacity)
			.maxConcurrentStreams(maxConcurrentStream)
			.initialWindowSize(initialWindowSize)
			.maxHeaderListSize(maxHeaderListSize);
	}
	public long maxHeaderListSize(){
		Long v = makeSetting().maxHeaderListSize();
		return v != null ? v : DEFAULT_HEADER_LIST_SIZE;
	};
	public Http2ChannelConfigure setInitialCapacity(int initialCapacity) {
		this.initialCapacity = initialCapacity;
		return this;
	}

	public Http2ChannelConfigure setHttp2AwaitSettingSeconds(int http2AwaitSettingSeconds) {
		this.http2AwaitSettingSeconds = http2AwaitSettingSeconds;
		return this;
	}

	public Http2ChannelConfigure setMaxConcurrentStream(long maxConcurrentStream) {
		this.maxConcurrentStream = maxConcurrentStream;
		return this;
	}

	public Http2ChannelConfigure setMaxHeaderListSize(long maxHeaderListSize) {
		this.maxHeaderListSize = maxHeaderListSize;
		return this;
	}

	public Http2ChannelConfigure setInitialWindowSize(int initialWindowSize) {
		this.initialWindowSize = initialWindowSize;
		return this;
	}

	public Http2ChannelConfigure setEncoderEnforceMaxConcurrentStreams(boolean encoderEnforceMaxConcurrentStreams) {
		this.encoderEnforceMaxConcurrentStreams = encoderEnforceMaxConcurrentStreams;
		return this;
	}

	public Http2ChannelConfigure setIsUpgradeHttp2(boolean isUpgradeHttp2) {
		this.isUpgradeHttp2 = isUpgradeHttp2;
		return this;
	}
	public Http2ChannelConfigure setMaxContentLength(int maxContentLength) {
		this.maxContentLength = maxContentLength;
		return this;
	}
	@Override
	public boolean diff(Http2ChannelConfigure o) {
//		if (super.diff(obj)) return true;
//		if (!(obj instanceof Http2ChannelConfigure)) return true;
//		Http2ChannelConfigure o = (Http2ChannelConfigure) obj;
		return o.maxContentLength != maxContentLength
			|| o.initialCapacity != initialCapacity
			|| o.http2AwaitSettingSeconds != http2AwaitSettingSeconds
			|| o.maxConcurrentStream != maxConcurrentStream
			|| o.maxHeaderListSize != maxHeaderListSize
			|| o.initialWindowSize != initialWindowSize
			|| o.encoderEnforceMaxConcurrentStreams != encoderEnforceMaxConcurrentStreams
			|| o.isUpgradeHttp2 != isUpgradeHttp2
			|| o.ignoreUpgradeHttp2 != ignoreUpgradeHttp2
			|| o.pingTimeoutMs != pingTimeoutMs
			;
	}
}
