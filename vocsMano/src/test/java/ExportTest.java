import com.viettel.vocs.common.file.JsonParser;
import com.viettel.vocs.microchassis.connection.ConnectionManager;
import org.junit.jupiter.api.Test;

/**
 * @author tiennn18
 */
public class ExportTest {
	@Test
	public void exportConnYml() throws Exception {
		ConnectionManager.getInstance().loadYmlConfig();
		System.out.println(ConnectionManager.getInstance().getConfiguration() != null
			? JsonParser.getInstance().writeAsJsonString(ConnectionManager.getInstance().getConfiguration())
			: "not yet loaded");

	}
}
