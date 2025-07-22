package com.viettel.vocs.microchassis.tracing.model;

public enum RequestType {
    INSERT("init"),
    UPDATE("update"),
    DELETE("delete"),
    NOTIFY("notify"),
    OTHER("other");

    private String tagString;

    RequestType(String tagString) {
        this.tagString = tagString;
    }

    public String getTagString() {
        return tagString;
    }

    public RequestType setTagString(String tagString) {
        if (!RequestType.OTHER.equals(this)) {
            return this;
        }
        this.tagString = tagString;
        return this;
    }

}
