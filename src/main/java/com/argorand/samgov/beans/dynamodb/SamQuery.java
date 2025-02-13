package com.argorand.samgov.beans.dynamodb;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
public class SamQuery {

    private String lastProcessedAt;
    private String queryUrl;
    private String queryDescription;
    private String email;
    private Set<String> processedOpportunities;

    @DynamoDbPartitionKey
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
