package com.argorand.samgov.beans;

import java.util.List;

import org.springframework.context.annotation.Description;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Result {
    @JsonProperty("isCanceled")
    private boolean isCanceled;

    @JsonProperty("_rScore")
    private int rScore;

    @JsonProperty("_type")
    private String type;

    @JsonProperty("publishDate")
    private String publishDate;

    @JsonProperty("isActive")
    private boolean isActive;

    @JsonProperty("title")
    private String title;

    @JsonProperty("type")
    private Type typeInfo;

    @JsonProperty("descriptions")
    private List<Description> descriptions;

    @JsonProperty("solicitationNumber")
    private String solicitationNumber;

    @JsonProperty("responseDate")
    private String responseDate;

    @JsonProperty("parentNoticeId")
    private String parentNoticeId;

    @JsonProperty("award")
    private Award award;

    @JsonProperty("responseTimeZone")
    private String responseTimeZone;

    @JsonProperty("modifiedDate")
    private String modifiedDate;

    @JsonProperty("organizationHierarchy")
    private List<Organization> organizationHierarchy;

    @JsonProperty("_id")
    private String id;

    @JsonProperty("responseDateActual")
    private String responseDateActual;

    public void setCanceled(boolean isCanceled) {
        this.isCanceled = isCanceled;
    }

    public void setrScore(int rScore) {
        this.rScore = rScore;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setPublishDate(String publishDate) {
        this.publishDate = publishDate;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setTypeInfo(Type typeInfo) {
        this.typeInfo = typeInfo;
    }

    public void setDescriptions(List<Description> descriptions) {
        this.descriptions = descriptions;
    }

    public void setSolicitationNumber(String solicitationNumber) {
        this.solicitationNumber = solicitationNumber;
    }

    public void setResponseDate(String responseDate) {
        this.responseDate = responseDate;
    }

    public void setParentNoticeId(String parentNoticeId) {
        this.parentNoticeId = parentNoticeId;
    }

    public void setAward(Award award) {
        this.award = award;
    }

    public void setResponseTimeZone(String responseTimeZone) {
        this.responseTimeZone = responseTimeZone;
    }

    public void setModifiedDate(String modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public void setOrganizationHierarchy(List<Organization> organizationHierarchy) {
        this.organizationHierarchy = organizationHierarchy;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setResponseDateActual(String responseDateActual) {
        this.responseDateActual = responseDateActual;
    }

    public boolean isCanceled() {
        return isCanceled;
    }

    public int getrScore() {
        return rScore;
    }

    public String getType() {
        return type;
    }

    public String getPublishDate() {
        return publishDate;
    }

    public boolean isActive() {
        return isActive;
    }

    public String getTitle() {
        return title;
    }

    public Type getTypeInfo() {
        return typeInfo;
    }

    public List<Description> getDescriptions() {
        return descriptions;
    }

    public String getSolicitationNumber() {
        return solicitationNumber;
    }

    public String getResponseDate() {
        return responseDate;
    }

    public String getParentNoticeId() {
        return parentNoticeId;
    }

    public Award getAward() {
        return award;
    }

    public String getResponseTimeZone() {
        return responseTimeZone;
    }

    public String getModifiedDate() {
        return modifiedDate;
    }

    public List<Organization> getOrganizationHierarchy() {
        return organizationHierarchy;
    }

    public String getId() {
        return id;
    }

    public String getResponseDateActual() {
        return responseDateActual;
    }
}
