package com.argorand.samgov.lambda;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.argorand.samgov.beans.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

@SpringBootApplication 
public class SamNotifier {

    @Value("${aws.region}")
    private String region;

    @Value("${aws.dynamodb.endpoint:}")
    private String dynamoDbEndpoint;

    @Value("${SAVED_QUERIES_TABLE:__FIXME__MISSING_TABLE_NAME}")
    private String savedQueriesTable;

    private ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient client = HttpClient.newHttpClient();

    private final Logger log = LoggerFactory.getLogger(SamNotifier.class);

    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        // Override endpoint if specified (useful for local development)
        if (dynamoDbEndpoint != null && !dynamoDbEndpoint.isEmpty()) {
            builder.endpointOverride(java.net.URI.create(dynamoDbEndpoint));
        }

        return builder.build();
    }    

    @Bean
    public Supplier<Void> checkQueryUpdates(DynamoDbClient dynamoDbClient) {
        return () -> {
            ScanResponse scanResponse;
            do {
                // Create a ScanRequest
                ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(savedQueriesTable)
                    .build();
                scanResponse = dynamoDbClient.scan(scanRequest);

                for (Map<String, AttributeValue> item : scanResponse.items()) {
                    var q = item.get("query");
                    log.info(describeUrl(q.s()));


                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(q.s()))
                                .header("Accept", "application/hal+json")
                                .GET()
                                .build();
            
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        ApiResponse apiResponse = objectMapper.readValue(response.body(), ApiResponse.class);
                        log.info("Response JSON: " + response.body());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }

                // Set the ExclusiveStartKey to continue scanning if there are more items
                scanRequest = scanRequest.toBuilder()
                        .exclusiveStartKey(scanResponse.lastEvaluatedKey())
                        .build();
            } while (scanResponse.lastEvaluatedKey() != null && !scanResponse.lastEvaluatedKey().isEmpty());
            return null;
        };
    }


    public static String describeUrl(String url) {
        if (url == null || !url.contains("?")) {
            return "Invalid or missing search URL.";
        }

        // Extract query parameters
        String queryString = url.substring(url.indexOf("?") + 1);
        String[] params = queryString.split("&");
        Map<String, String> queryMap = new HashMap<>();

        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2) {
                queryMap.put(keyValue[0], keyValue[1]);
            } else {
                queryMap.put(keyValue[0], "");
            }
        }

        // Build human-readable description
        return queryMap.entrySet().stream()
                .map(entry -> parseParameter(entry.getKey(), entry.getValue()))
                .filter(desc -> !desc.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    private static String parseParameter(String key, String value) {
        switch (key) {
            case "q":
                return "Keyword: " + value;
            case "response_date.to":
                return "Response deadline (to): " + value;
            case "response_date.from":
                return "Response deadline (from): " + value;
            case "modified_date.to":
                return "Modified date (to): " + value;
            case "modified_date.from":
                return "Modified date (from): " + value;
            case "naics":
                return "NAICS code: " + value;
            case "psc":
                return "PSC codes: " + value.replace(",", ", ");
            case "organization_id":
                return "Org ID: " + value;
            case "vendor_name":
                return "Vendor name: " + value;
            case "ueiSAM":
                return "UEI SAM: " + value;
            case "notice_type":
                return "Notice type: " + value;
            case "set_aside":
                return "Set-aside: " + value;
            default:
                return ""; // Skip unknown parameters
        }
    }



    public static void main(String[] args) {

    }
}
