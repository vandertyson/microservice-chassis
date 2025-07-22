package com.viettel.vocs.mano.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@ToString
public class ConfigAPIResponse {
    @Getter @Setter private String status;
    @Getter @Setter
    private String message;
    @Getter private Map<String, Object> data;

    public ConfigAPIResponse(String _status, String _message) {
        this.status = _status;
        this.message = _message;
    }

    public ConfigAPIResponse setData(Map<String, Object> data) {
        this.data = data;
        return this;
    }
}
