package com.viettel.vocs.mano.reload;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class ReloadAPIInfo {
    @Getter @Setter private String name;
    @Getter @Setter private String key;
    @Getter @Setter private List<String> reloadType;
    @Getter @Setter private List<ReloadAPIParam> listParam;
}
