package com.argorand.samgov.beans.dynamodb;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

@DynamoDbBean
public class SamQuery {

    private String notificationId;
    private String userId;
    private String createdAt;
    private String lastProcessedAt;
    private String queryUrl;
    private String queryDescription;
    private String email;
    private Set<String> processedOpportunities;
    private Integer version;

    @DynamoDbPartitionKey
    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "gsi_userId_createdAt")
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @DynamoDbSecondarySortKey(indexNames = "gsi_userId_createdAt")
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getLastProcessedAt() {
        return lastProcessedAt;
    }

    public void setLastProcessedAt(String lastProcessedAt) {
        this.lastProcessedAt = lastProcessedAt;
    }

    @DynamoDbAttribute("query")
    public String getQueryUrl() {
        return queryUrl;
    }

    public void setQueryUrl(String queryUrl) {
        this.queryUrl = queryUrl;
    }

    @DynamoDbAttribute("user_description")
    public String getQueryDescription() {
        return queryDescription;
    }

    public void setQueryDescription(String queryDescription) {
        this.queryDescription = queryDescription;
    }

    @DynamoDbAttribute("email_sent")
    public Set<String> getProcessedOpportunities() {
        return processedOpportunities;
    }

    public void setProcessedOpportunities(Set<String> processedOpportunities) {
        this.processedOpportunities = processedOpportunities;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public void addProcessedOpportunities(List<String> opportunities) {

        if(this.getProcessedOpportunities() == null) {
            var set = new HashSet<String>();
            set.addAll(opportunities);
            this.setProcessedOpportunities(set);
        } else {
            this.getProcessedOpportunities().addAll(opportunities);
        }
    }
}
