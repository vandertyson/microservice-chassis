package com.viettel.vocs.microchassis.connection;

import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.thread.ThreadManager;
import com.viettel.vocs.microchassis.connection.event.ClientEvent;
import com.viettel.vocs.microchassis.connection.event.EventHandler;
import com.viettel.vocs.microchassis.connection.event.InternalEventHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventHandlerTest {
    protected static final Logger logger = LogManager.getLogger(EventHandlerTest.class);

    @BeforeAll
    public void setUpClass(){
        LogUtils.setupLog4j2();
    }
    @Test
    void testDefault() {
        EventHandler handler = new InternalEventHandler("test-client", ThreadManager.sharedThreadPoolExecutor);
        handler.addEventHandler(ClientEvent.EventType.DISCOVERY_FAIL, (Consumer<ClientEvent>) pr -> {
            assertEquals("abc", pr.getServiceName());
            logger.info("#######OK");
        });
        handler.addEventHandler(ClientEvent.EventType.DISCOVERY_SUCCESS, (Consumer<ClientEvent>) pr -> {
            assertEquals("abc", pr.getServiceName());
            logger.info("#######OK 1");
        });
        handler.triggerEvent(new ClientEvent(ClientEvent.EventType.DISCOVERY_FAIL, null,  "abc not found"));
        handler.addEventHandler(ClientEvent.EventType.DISCOVERY_FAIL, (Consumer<ClientEvent>) pr -> {
            assertEquals("abc", pr.getServiceName());
            logger.info("#######OK 2");
        });
        handler.triggerEvent(new ClientEvent(ClientEvent.EventType.DISCOVERY_FAIL, null, "abc not found"));
    }
}
