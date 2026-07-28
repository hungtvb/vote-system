package com.hungtvb.votesystem.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PreferredLocale {
    VI("vi"),
    EN("en");

    private final String code;

    PreferredLocale(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static PreferredLocale fromCode(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.strip().toUpperCase(Locale.ROOT));
    }
}
