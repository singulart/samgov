package com.argorand.samgov.lambda;

import java.net.URI;
import java.net.http.HttpRequest;

public class RestRequestFactory {

    private static final String SAMGOV_API_CONTENT_TYPE = "application/hal+json";

    public static HttpRequest buildMainRestQuery(String url) {
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", SAMGOV_API_CONTENT_TYPE)
            .GET()
            .build();
    }
    
    public static HttpRequest buildGetOpportunityQuery(String id) {
        return HttpRequest.newBuilder()
            .uri(URI.create(String.format("https://sam.gov/api/prod/opps/v2/opportunities/%s", id)))
            .header("Accept", SAMGOV_API_CONTENT_TYPE)
            .GET()
            .build();
    }
}
