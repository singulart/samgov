package com.argorand.samgov.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Organization {
    @JsonProperty("organizationId")
    private String organizationId;

    @JsonProperty("address")
    private Address address;

    @JsonProperty("level")
    private int level;

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;

    @JsonProperty("status")
    private String status;

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public Address getAddress() {
        return address;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }    
}
