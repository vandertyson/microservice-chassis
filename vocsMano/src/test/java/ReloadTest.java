import com.viettel.vocs.common.os.TimeUtils;
import com.viettel.vocs.mano.model.BlueGreenUpdate;
import com.viettel.vocs.mano.service.Mano1Handler;
import com.viettel.vocs.microchassis.codec.context.http.HttpServerContext;
import com.viettel.vocs.microchassis.http.config.HttpServerOptions;
import com.viettel.vocs.microchassis.http.server.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author vttek
 */
class HttpServerHandler extends Mano1Handler {
	private static final Logger logger = LogManager.getLogger(HttpServerHandler.class);

	public HttpServerHandler(HttpServerOptions config) {
		super(config);
	}

	@Override
	public void healthCheck(HttpServerContext serverContext) {

	}

	@Override
	public void metrics(HttpServerContext serverContext) {

	}

	@Override
	public void postHandle(HttpServerContext serverCtx) {
		try {
			if (serverCtx.getInPath().contains("/reload"))
				serverCtx.send("Reload " + serverCtx.getInPath() + " success");
		} catch (Exception e) {
			logger.error(e, e);
		}
	}

	@Override
	public void shutdown(HttpServerContext ctx) {
	}

	@Override
	public void blueGreenUpdate(HttpServerContext serverContext, BlueGreenUpdate blueGreenUpdate) {
		if (logger.isTraceEnabled()) {
			logger.trace("[blueGreenUpdate]");
		}
	}
}


public class ReloadTest {
	public static void main(String[] args) throws Exception {
		String reloadString = "curl localhost:9000/reloadTest -d '{\"reloadKey\": \"testPolicy\n" +
			"\", \"reloadInfo\": \"1234\"}'";
		HttpServer h1server = new HttpServerOptions("reloadHserver").newServer();
		h1server.start(new HttpServerHandler(h1server.getConfig()));

		TimeUtils.waitSafeMili(10000000);
	}
}
