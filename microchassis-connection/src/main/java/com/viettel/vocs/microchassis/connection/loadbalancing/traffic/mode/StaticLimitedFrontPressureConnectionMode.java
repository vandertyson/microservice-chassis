package com.viettel.vocs.microchassis.connection.loadbalancing.traffic.mode;

import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.FrontConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.configure.StrategyConfigure;
import com.viettel.vocs.microchassis.connection.loadbalancing.traffic.monitor.PeerCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author tiennn18
 * Front pressure with max limit TPS changed by rules
 */
public abstract class StaticLimitedFrontPressureConnectionMode implements ConnectionMode {
	/**
	 * set step:
	 * 1. trong window chi duoc gui toi da (step), cach deu theo getMinWait()
	 * 2. trong window can nhan du (step), neu khong nhan du:
	 * a) cac request send di nhung khong thay tra ve -> thi giu nguyen toc do cho den khi du
	 * b) khong co request de gui -> wait cho den khi co request
	 */
	public static class LadderConfigure extends StepConfigure {
		public LadderConfigure(int baseTps, int nStep, int maxPerciselyTps, long windowNs, PeerCounter counter) {
			super(counter);
			this.tpsWindow.set(windowNs);
			for (int i = 0; i < nStep; i++) {
				int tps = baseTps * nStep;
				int toSetTPS = Math.min(tps, maxPerciselyTps);
				addStep(toSetTPS);
				if(toSetTPS == maxPerciselyTps) break;
			}
		}
	}
	public static class Exp2Configure extends StepConfigure {
		public Exp2Configure(int maxExp, long windowNs, int maxPerciselyTps, PeerCounter counter) {
			super(counter);
			this.tpsWindow.set(windowNs);
			for (int i = 0; i <= maxExp; i++) {
				int tps = (int) (Math.pow(2, maxExp) % Integer.MAX_VALUE);
				int toSetTPS = Math.min(tps, maxPerciselyTps);
				addStep(toSetTPS);
				if(toSetTPS == maxPerciselyTps) break;
			}
		}
	}
	public static class StepConfigure extends FrontConfigure {
		protected final PeerCounter counter;
		List<Integer> tpsSteps = new ArrayList<>();
		/**
		 * non-shock config
		 */
		public StepConfigure(PeerCounter counter) {
			super();
			this.counter = counter;
		}
		@Override
		public boolean diff(StrategyConfigure obj) {
			if(super.diff(obj)) return true;
			if(!( obj instanceof StepConfigure)) return true;
			StepConfigure o = (StepConfigure) obj;
			return !Objects.equals(tpsSteps, o.tpsSteps);
		}
		public StepConfigure(long stepWindow, PeerCounter counter) {
			this(counter);
			if (stepWindow > 0) tpsWindow.set(stepWindow);
		}

		public StepConfigure(List<Integer> tpsSteps, long stepWindow, PeerCounter counter) {
			this(tpsSteps, counter);
			if (stepWindow > 0) tpsWindow.set(stepWindow);
		}

		public StepConfigure(List<Integer> tpsSteps, PeerCounter counter) {
			this(counter);
			this.tpsSteps = tpsSteps;
		}

		public void sort() {
			// chi sort khi step map can asc/desc, binh thuong khong can sort, stepmap theo yeu cau
			Collections.sort(tpsSteps);
		}

		int stepPointer = 0;
		long lastStepUp = TimeUtils.nowNano();


		public boolean isEnd() {
			return stepPointer >= tpsSteps.size();
		}

		public boolean addStep(int tps) {
			if (tps < maxPeakTPS) {
				tpsSteps.add(tps);
				return true;
			}
			return false;
		}

		public int countSteps() {
			return tpsSteps.size();
		}

		public boolean isValid() {
			return countSteps() > 0;
		}

		public int getStep() {
			return tpsSteps.get(stepPointer);
		}
		public int reset(){
			stepPointer = 0;
			return getStep();
		}

		public int nextStep() { // can multiple time trigger
			if (TimeUtils.nowNano() > lastStepUp + tpsWindow.get()) {
				int lastTPS = getStep();
				stepPointer++;
				lastStepUp = TimeUtils.nowNano();
				return lastTPS;
			}
			return 0;
		}

		@Override
		public long getMinWait() {
			return tpsWindow.get() / getStep();
		}

		@Override
		public int getTPS() {
			return getStep();
		}

		@Override
		public long getWindow() {
			return tpsWindow.get();
		}

		@Override
		public boolean changeTPS(int newTps) {
			// static co lo trinh roi, nen khong cho change, chi co doi ConnectionMode
			return false;
		}

		@Override
		public boolean changeTPS(int newTps, int newShockTps) {
			// static co lo trinh roi, nen khong cho change, chi co doi ConnectionMode
			return false;
		}
	}

	@Override
	public boolean isEnd() {
		return stepper.isEnd();
	}

	@Override
	public void close() {
		stepper.stepPointer = stepper.tpsSteps.size(); // to end state immediately
	}


	@Override
	public String report() {
		return String.format("%d", stepper.getStep());
	}

	@Override
	public StrategyConfigure getConfig() {
		return stepper;
	}

	@Override
	public long getWindow() {
		return stepper.getWindow();
	}

	@Override
	public long getMinWait() {
		return stepper.getMinWait();
	}

	long thisStepReceivedOffset;
	protected boolean nextStepConditionCheck(){
		return stepper.counter.getReceived() - thisStepReceivedOffset >= stepper.getStep();
	}
	@Override
	public int getTps() {
		/**
		 * cap nhat noi bo, limiter se dinh ky goi check vao de lay ticket,
		 * 	khong can quan tam toi limiter ben ngoai
		 */
		if (!stepper.isEnd()) { // Thread se chay khi Connection mode con cap nhat, neu khong cap nhat nua thread nay co the end
			while (nextStepConditionCheck()) {
				// neu nhan du roi thi nhay nextStep
				thisStepReceivedOffset += stepper.nextStep();
			}
			// trong nextstep da co check time required, neu khong duoc se thu lai sau
		}
		return stepper.getStep();
	}
	public int reset(){
		return stepper.reset();
	}
	public static StepConfigure generate1kStepConfigure(List<Integer> tpsSteps, long windowNs, PeerCounter counter) {
		StepConfigure sc = new StepConfigure(
			tpsSteps.stream().filter(tps -> tps > 0 && tps <= 1000).collect(Collectors.toList()),
			windowNs, counter);
		return sc.isValid() ? sc : new LadderConfigure(1, 10, 10, windowNs, counter); // 1-1024TPS
	}

	protected final StepConfigure stepper;

	public StepConfigure getStepper() {
		return stepper;
	}

	protected StaticLimitedFrontPressureConnectionMode(StepConfigure stepper) {
		this.stepper =stepper;
		stepper.sort();
	}
	protected StaticLimitedFrontPressureConnectionMode(List<Integer> tpsSteps, long windowNs, PeerCounter counter) {
		this(generate1kStepConfigure(tpsSteps, windowNs, counter));
	}
}
