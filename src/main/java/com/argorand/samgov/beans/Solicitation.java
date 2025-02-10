package com.argorand.samgov.beans;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Solicitation {

    @JsonProperty("description")
    private List<Description> descriptions;

    @JsonProperty("id")
    private String id;

    public List<Description> getDescriptions() {
        return descriptions;
    }

    public void setDescriptions(List<Description> descriptions) {
        this.descriptions = descriptions;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
