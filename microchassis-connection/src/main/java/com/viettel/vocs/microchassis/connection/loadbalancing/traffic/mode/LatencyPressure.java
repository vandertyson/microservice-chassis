package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author tiennn18
 * based on TCP CCA BBR algorithm
 * gain +-0.25
 * each press is 1 RTT
 * tiennn18: set start TPS = maxTPS/10, after 10 RTT gain equal 0.931 maxTPS so
 */
public class LatencyPressure {
	private final Map<String, KPI> kpiMap;

	public LatencyPressure(Map<String, KPI> kpiMap) {
		this.kpiMap = kpiMap;
	}
	float keenGain = 1.00f;
	float downGain = 0.75f;
	float upGain = 1.25f;
	float outOfKPIrate; // k, number of request out of KPI rate
	final Map<Long, List<Long>> historyMap = new ConcurrentHashMap<>(); // startStamp: list latencies
	public void shiftWindow(long newWindowStamp, long firstValue){
		List<Long> newSlice = new ArrayList<>();
		newSlice.add(firstValue);
		historyMap.put(newWindowStamp, newSlice);
		if(historyMap.size() > 10) historyMap.remove(Collections.min(historyMap.keySet())); // keep only 10 windows
	}
	// TODO can viet ham rotate avg
	long avgOutOfKPI; // T, de thoa man truong hop giam TPS: T > t(k+1)/k thi voi cac request co thoi gian phan hoi > t(k+1)/k ta se danh gia bang t(k+1)/k
	long avgUnderKPI=0; // t
	public int down(int currentTPS){ // my estimate based on compute power
		int newTPS = currentTPS;
		if (avgOutOfKPI > (1 + 1 / outOfKPIrate) * avgUnderKPI) { // condition for exist decreaseRate (mau so > 0))
			if (avgOutOfKPI < avgUnderKPI * (1 + 2 * outOfKPIrate) / 2 / outOfKPIrate
				// && avgOutOfKPI >= avgUnderKPI // luon dung
				// && outOfKPIrate >= 0 // luon dung
			) { // condition for 0 <= decreaseRate < 1
				float outOfTimeValue = (avgOutOfKPI - avgUnderKPI) * outOfKPIrate;
				float decreaseRate = outOfTimeValue / (avgUnderKPI - outOfTimeValue);
				newTPS = (int) Float.max((newTPS * (1 - decreaseRate)), 1);
			} else { // neu decreaseRate qua lon -> giam het ve 1 TPS
				newTPS = 1;
			}
		} else { // neu khong thoa man mau so > 0, tuc la T qua lon, hay he thong khong du nang luc xu ly P cho request F (F>P) -> tps = n = 1
			newTPS = 1;
		}
		return newTPS;
	}
	private int BBRbasedAlgo(int currentTPS, KPI kpi){ // TCP BBR algorithm Google@2016@LinuxKernel4.9
		if (outOfKPIrate < kpi.upRate) {
			// case up
			return (int) Math.ceil(currentTPS*upGain);
		} else if (outOfKPIrate < kpi.keepRate) {
			return (int) Math.ceil(currentTPS*keenGain);
		} else if (outOfKPIrate < kpi.downRate) {
			// case down
			return (int) Math.ceil(currentTPS*downGain);
		} else { // reset rate
			return 1;
		}
	}

	public int press(int currentTPS, String apiKpiName) {
		return 0; //pressNext(BBRbasedAlgo(currentTPS, kpiMap.get(apiKpiName)), apiKpiName);
	}
}
