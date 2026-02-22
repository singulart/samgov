package com.argorand.samgov.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Awardee {
    
    @JsonProperty("ueiSAM")
    private String ueiSAM;

    public String getUeiSAM() {
        return ueiSAM;
    }

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    private String name;

    public void setUeiSAM(String ueiSAM) {
        this.ueiSAM = ueiSAM;
    }

    public void setName(String name) {
        this.name = name;
    }
}
