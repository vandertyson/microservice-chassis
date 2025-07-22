package com.viettel.autotest.microchassis.lib.generator;

import com.viettel.autotest.microchassis.ChassisAutotestConfig;
import com.viettel.vocs.microchassis.codec.ChassisMgrableMsg;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * * Created on 19/10/2021
 */
public interface PayloadGenerator<Req extends ChassisMgrableMsg, Allocer> {
	Logger logger = LogManager.getLogger(PayloadGenerator.class);
	String testUrl = System.getProperty("testUrl", "/test");

	class MsisdnRange extends MutableTriple<Long /*begin*/, AtomicLong /*current*/, Long /*end*/> {
		public MsisdnRange(long begin, long end) {
			left = begin;
			right = end;
			middle = new AtomicLong(begin);
		}

		public String next() {
			long next = middle.getAndIncrement();
			if (next > right) {
				middle.set(left);
				next = left;
			}
			return next + "";
		}
	}

	Function<String /*path*/, String> getFileMethod = path -> {
		try {
			return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return null;
		}
	};
	boolean hasPayloadFile = ChassisAutotestConfig.payloadFile.get() != null && (new File(ChassisAutotestConfig.payloadFile.get())).exists();
	String payloadString = hasPayloadFile ? getFileMethod.apply(ChassisAutotestConfig.payloadFile.get()) : null;
	int beginIdx = payloadString != null ? payloadString.indexOf(ChassisAutotestConfig.msisdnPattern.get()) : -1;
	int endIdx = beginIdx != -1 ? beginIdx + ChassisAutotestConfig.msisdnPattern.get().length() : -1;


	List<MsisdnRange> listMsisdn = Arrays.stream(System.getProperty("msisdnRange", "8490000000:8499999999,8430000000:8439999999").split("[,;]")).map(range -> {
		String[] strings = range.trim().split(":");
		return new MsisdnRange(Long.parseLong(strings[0]), Long.parseLong(strings[1]));
	}).collect(Collectors.toList());
	AtomicLong reqID = new AtomicLong();
	Req generateRandomBinaryRequest(ByteBuf content);
	default ByteBufAllocator getAllocator(){
		return Unpooled.buffer().alloc();
	}
	default Req generateRandomBinaryRequest(int size) {
		return generateRandomBinaryRequest(getAllocator(), size);
	}
	default Req generateRandomBinaryRequest(ByteBufAllocator allocator, int size) {
		return generateRandomBinaryRequest(allocator.buffer(size).resetReaderIndex().writeBytes(new byte[size]));
	}
	static byte[] nextOcsPayload() {
		if (payloadString != null) {
			String msisdn = nextMsisdn();
			StringBuilder newPayload = new StringBuilder(payloadString).replace(beginIdx, endIdx, msisdn);
			if (logger.isDebugEnabled()) {
				logger.debug("Payload: " + newPayload);
			}
			return newPayload.toString().getBytes(StandardCharsets.UTF_8);
		}
		return new byte[4096];
	}

	static String nextMsisdn() {
		long next = reqID.incrementAndGet();
		if (next == Long.MAX_VALUE) {
			reqID.set(0);
			next = reqID.incrementAndGet();
		}
		long index = next % listMsisdn.size();
		MsisdnRange range = listMsisdn.get((int) index);
		return range.next();
	}
}

