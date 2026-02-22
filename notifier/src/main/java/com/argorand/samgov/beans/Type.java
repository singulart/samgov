package com.argorand.samgov.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Type {

    @JsonProperty("code")
    private String code;

    @JsonProperty("value")
    private String value;

    public void setCode(String code) {
        this.code = code;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getCode() {
        return code;
    }

    public String getValue() {
        return value;
    }
}
