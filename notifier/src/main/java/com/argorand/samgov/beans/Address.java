package com.argorand.samgov.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Address {
    @JsonProperty("zip")
    private String zip;

    @JsonProperty("country")
    private String country;

    @JsonProperty("city")
    private String city;

    @JsonProperty("streetAddress")
    private String streetAddress;

    @JsonProperty("streetAddress2")
    private String streetAddress2;

    public void setZip(String zip) {
        this.zip = zip;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public void setStreetAddress2(String streetAddress2) {
        this.streetAddress2 = streetAddress2;
    }

    public void setState(String state) {
        this.state = state;
    }

    @JsonProperty("state")
    private String state;

    public String getZip() {
        return zip;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public String getStreetAddress2() {
        return streetAddress2;
    }

    public String getState() {
        return state;
    }    
}
