package com.viettel.vocs.microchassis.connection.event;

public abstract class InternalEvent {
    public final EventCatalog type;
    public final String message;
    public abstract EventCatalog getType();
//    protected Map<String, ?> params = new HashMap<>();

    protected InternalEvent(EventCatalog type, String message) {
        this.type = type;
        this.message = message;
    }
    protected InternalEvent(EventCatalog type) {
        this(type, "");
    }

//    public Map<String, ?> getParams() {
//        return params;
//    }
//
//    public EventParam<E> setParams(Map<String, ?> params) {
//        this.params = params;
//        return this;
//    }

}
