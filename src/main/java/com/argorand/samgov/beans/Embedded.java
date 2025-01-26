package com.argorand.samgov.beans;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Embedded {
    @JsonProperty("results")
    private List<Result> results;

    // Getters and Setters
    public List<Result> getResults() {
        return results;
    }

    public void setResults(List<Result> results) {
        this.results = results;
    }    
}
