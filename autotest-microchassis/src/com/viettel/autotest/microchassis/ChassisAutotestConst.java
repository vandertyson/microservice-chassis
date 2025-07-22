package com.viettel.autotest.microchassis;

public class ChassisAutotestConst {
	// long, time
	public static final int byteTimeFromBorn = 0; // 8#0-7
	public static final int byteTimeRecvFromLastClient = 8; // 8#8-15
	public static final int byteTimeSendFromLastClient = 16; // 8#16-23
	public static final int byteTimeSendFromLastServer = 24; // 8#24-31
	public static final int byteDrop = 24; // 1#32
	// char seqs
	public static final int byteMessID = 52; // 40#52-91
  // payload
	public static final int bytePayload = 500;
	public static final int byteTimeSendFromClient = 0;
	public static final int byteTimeReceiveAtGW = 8;
	public static final int byteTimeReceiveAtA = 16;
	public static final int byteTimeSendToB = 64;
	public static final int byteTimeSendToC = 80;
}
