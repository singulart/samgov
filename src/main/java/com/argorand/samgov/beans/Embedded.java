package com.argorand.samgov.beans;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Embedded {
    @JsonProperty("results")
    private List<Result> results = new ArrayList<>();

    // Getters and Setters
    public List<Result> getResults() {
        return Optional.ofNullable(results).orElse(new ArrayList<>());
    }

    public void setResults(List<Result> results) {
        this.results = results;
    }    
}
