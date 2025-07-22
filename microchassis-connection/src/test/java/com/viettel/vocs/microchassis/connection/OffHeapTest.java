package com.viettel.vocs.microchassis.connection;

import com.viettel.vocs.common.log.LogUtils;
import com.viettel.vocs.common.os.TimeUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.NetUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.Random;

public class OffHeapTest {
    protected static final Logger logger = LogManager.getLogger(OffHeapTest.class);

    @BeforeAll
    public void setUpClass(){
        LogUtils.setupLog4j2();
    }
    @Test
    public void testOffHeap() throws InterruptedException {
        byte[] b = new byte[2048];
        new Random().nextBytes(b);
        PooledByteBufAllocator pba = new PooledByteBufAllocator(true);
        for (int i = 0; i < 10000; i++) {
            ByteBuf buffer = pba.compositeBuffer(2048);
            buffer.writeBytes(b);
        }
        System.out.print(pba.dumpStats());
        while (true) {
            TimeUtils.waitSafeMili(1000);
            System.out.println("direct " + pba.metric().usedDirectMemory());
            System.out.println("heap " + pba.metric().usedHeapMemory());
        }
    }

    @Test
    public void testNetty(){
        System.out.println(NetUtil.SOMAXCONN);
    }
}
