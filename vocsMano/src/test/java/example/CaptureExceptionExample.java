package example;

import com.viettel.vocs.common.CommonConfig;
import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.mano.service.ManoIntegration;
import com.viettel.vocs.microchassis.exception.client.ExceptionMonitor;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmClearedBuilder;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmEventBuilder;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes.EventType;
import com.viettel.vocs.microchassis.exception.data_model.message.AlarmTypes.PerceivedSeverityType;
import com.viettel.vocs.microchassis.exception.data_model.type.AlarmCode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Scanner;

public class CaptureExceptionExample {
	private static final Logger logger = LogManager.getLogger(CaptureExceptionExample.class);

	public static void main(String[] args) {
		logger.info("Start");
		new CaptureExceptionExample().run();
	}

	public void init() {
		CommonConfig.InstanceInfo.VDU_NAME.setDefault("CHP-service");
		ManoIntegration.startTrackingExceptions();
	}

	public void sendAlarmNotification() {
		AlarmEventBuilder ex = new AlarmEventBuilder()
			.withProbableCause("This is new exception")
			.withPerceivedSeverity(PerceivedSeverityType.WARNING)
			.withEventType(EventType.COMMUNICATIONS_ALARM);

		ExceptionMonitor.captureAlarm(ex, true, AlarmCode.FULL_QUEUE.toString());
	}

	public void sendAlarmNotification2() {
		try {
			throw new Exception("bla bla");
		} catch (Exception e) {
			ExceptionMonitor.captureAlarm(e, true, AlarmCode.FULL_QUEUE.toString());
		}
	}

	public void sendClearedNotification() {
		String alarmId = ExceptionMonitor.cacheAlarmId.get(AlarmCode.FULL_QUEUE);

		AlarmClearedBuilder alarmClearedBuilder = new AlarmClearedBuilder().withAlarmId(alarmId);

		ExceptionMonitor.captureAlarm(alarmClearedBuilder, false, null);
	}

	public void run() {
		this.init();
		Scanner scanner = new Scanner(System.in);
		boolean isRunning = true;

		while (isRunning) {
			try {
				System.out.println("Please input a line");
				String line = scanner.nextLine();
				System.out.printf("User input was: %s%n", line);
				if (line.equals("stop")) {
					isRunning = false;
				} else {
					logger.info("Generate AlarmEvent");

					this.sendAlarmNotification();
					this.sendClearedNotification();
				}
			} catch (Exception e) {
				logger.error(e, e);
			}
		}

		TimeUtils.waitSafeMili(1000);

		ExceptionMonitor.stop();
	}
}
