package com.argorand.samgov.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Description {

    @JsonProperty("lastModifiedDate")
    private String lastModifiedDate;

    @JsonProperty("body")
    private String body;

    public String getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(String lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
    
}