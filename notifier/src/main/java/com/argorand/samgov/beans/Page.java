package com.argorand.samgov.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Page {

    @JsonProperty("size")
    private int size;

    @JsonProperty("totalElements")
    private int totalElements;

    @JsonProperty("totalPages")
    private int totalPages;

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(int totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public int getMaxAllowedRecords() {
        return maxAllowedRecords;
    }

    public void setMaxAllowedRecords(int maxAllowedRecords) {
        this.maxAllowedRecords = maxAllowedRecords;
    }

    @JsonProperty("number")
    private int number;

    @JsonProperty("maxAllowedRecords")
    private int maxAllowedRecords;
    
}
