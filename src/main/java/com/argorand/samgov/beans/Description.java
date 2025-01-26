package com.argorand.samgov.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Description {

    @JsonProperty("lastModifiedDate")
    private String lastModifiedDate;

    @JsonProperty("content")
    private String content;

    public String getLastModifiedDate() {
        return lastModifiedDate;
    }

    public String getContent() {
        return content;
    }

    public void setLastModifiedDate(String lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public void setContent(String content) {
        this.content = content;
    }


}