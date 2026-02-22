package com.argorand.samgov.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Award {

    @JsonProperty("awardee")
    private Awardee awardee;

    public void setAwardee(Awardee awardee) {
        this.awardee = awardee;
    }

    public Awardee getAwardee() {
        return awardee;
    }
}
