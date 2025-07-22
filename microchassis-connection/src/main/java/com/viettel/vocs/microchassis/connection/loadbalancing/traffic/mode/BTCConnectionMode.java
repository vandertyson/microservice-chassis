package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

import com.viettel.vocs.microchassis.connection.loadbalancing.configure.BTC;

public class BTCConnectionMode extends BackPressureConnectionMode {
	public BTCConnectionMode(String channelId, BTC btcConfigure) {
		super(new BTCPressure(channelId, btcConfigure));
	}

	public boolean isUnderPressure() {
		BTCPressure bccPressure = (BTCPressure) pressureSource;
		switch (bccPressure.getCurrentFsmState()){
			case 2: // max TPS
			case 4: // max CC
			case 7: // long run low tps but HL
				return bccPressure.estimateLoadStatelessOnline(); //isHighload
			case 1: // thieu TPS thieu CC
			case 3: // thieu TPS
			case 5: case 6: case 8: case 9: // cac case stable
			default:
				return false;
		}
	}
	@Override
	public String report() {
		return pressureSource.report(getBackConfigure(), state);
	}
}
