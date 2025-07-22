package com.viettel.vocs.mano.reload;

import lombok.Getter;
import lombok.Setter;

public class ReloadAPIParam {
    @Getter @Setter private String name;
    @Getter @Setter private String type;
    @Getter @Setter private String desc;
    @Getter @Setter private boolean required;
}
