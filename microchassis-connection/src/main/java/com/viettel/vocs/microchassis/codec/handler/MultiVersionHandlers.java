package com.viettel.vocs.microchassis.codec.handler;

import java.util.concurrent.ConcurrentHashMap;

public abstract class MultiVersionHandlers<Target> extends ConcurrentHashMap<String /*version*/, Target> {
}
