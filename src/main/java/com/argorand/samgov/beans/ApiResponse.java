package com.argorand.samgov.beans;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;


public class ApiResponse {

    @JsonProperty("_embedded")
    private Embedded embedded;

    @JsonProperty("page")
    private Page page;

    // Getters and Setters
    public Embedded getEmbedded() {
        return Optional.ofNullable(embedded).orElse(new Embedded());
    }

    public void setEmbedded(Embedded embedded) {
        this.embedded = embedded;
    }

    public Page getPage() {
        return page;
    }

    public void setPage(Page page) {
        this.page = page;
    }    
}
