package com.argorand.samgov.lambda;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.argorand.samgov.beans.Result;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.SesClientBuilder;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

@SpringBootApplication 
public class SamNotifier {

    @Value("${aws.region}")
    private String region;

    @Value("${aws.endpoint:}")
    private String awsEndpoint;

    @Value("${SAVED_QUERIES_TABLE:__FIXME__MISSING_TABLE_NAME}")
    private String savedQueriesTable;

    @Value("${SES_SENDER:api@argorand.io}")
    private String senderEmailAddress;

    private static final String PRIMARY_KEY_ATTRIBUTE = "lastProcessedAt";
    private static final String SENT_ATTRIBUTE = "email_sent";
    private static final String SAMGOV_API_CONTENT_TYPE = "application/hal+json";

    private ObjectMapper objectMapper = new ObjectMapper();

    {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    private HttpClient client = HttpClient.newHttpClient();

    private final Logger log = LoggerFactory.getLogger(SamNotifier.class);

    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (awsEndpoint != null && !awsEndpoint.isEmpty()) {
            builder.endpointOverride(java.net.URI.create(awsEndpoint));
        }
        return builder.build();
    }

    @Bean
    public SesClient sesClient() {
        SesClientBuilder builder = SesClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (awsEndpoint != null && !awsEndpoint.isEmpty()) {
            builder.endpointOverride(java.net.URI.create(awsEndpoint));
        }
        return builder.build();
    }

    @Bean
    public Supplier<Void> checkQueryUpdates(DynamoDbClient dynamoDbClient, SesClient sesClient) {
        return () -> {
            ScanResponse scanResponse;
            do {
                ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(savedQueriesTable)
                    .build();
                scanResponse = dynamoDbClient.scan(scanRequest);

                for (Map<String, AttributeValue> item : scanResponse.items()) {
                    var q = item.get("query");
                    var recipient = item.get("email").s();
                    log.info(SamUtils.describeUrl(q.s()));
                    try {
                        var preparedUrl = DateSubstitutor.updateUrl(q.s());
                        
                        log.info("Final URL: {}", preparedUrl);
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(preparedUrl))
                                .header("Accept", SAMGOV_API_CONTENT_TYPE)
                                .GET()
                                .build();
            
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        ApiResponse apiResponse = objectMapper.readValue(response.body(), ApiResponse.class);

                        if(apiResponse.getEmbedded().getResults().isEmpty()) {
                            log.info("No search results");
                        } else {
                            var alreadyProcessedIds = Optional.ofNullable(item.get(SENT_ATTRIBUTE).ss()).orElse(new ArrayList<>());
                            apiResponse.getEmbedded().getResults().removeIf(r -> alreadyProcessedIds.contains(r.getId()));
                            sendEmail(sesClient, senderEmailAddress, recipient, "SAM.gov query has new results", SamUtils.generateSummary(apiResponse), null);
                            var opportunityIds = apiResponse.getEmbedded().getResults().stream().map(Result::getId).collect(Collectors.toList());
                            saveProcessedOpportunities(dynamoDbClient, item.get(PRIMARY_KEY_ATTRIBUTE).s(), opportunityIds);
                        }
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

    void saveProcessedOpportunities(DynamoDbClient dynamoDbClient, String primaryKeyValue, List<String> opportunityIds) {

        // Convert the list of strings to a set of AttributeValue
        List<AttributeValue> newIdAttrs = opportunityIds.stream()
                .map(AttributeValue::fromS)
                .toList();

        // Update expression to append items to the "processed" list
        String updateExpression = "SET #attr = list_append(if_not_exists(#attr, :empty_list), :newValues)";

        // Define placeholders and values for the update expression
        Map<String, AttributeValue> expressionAttributeValues = Map.of(
                ":newValues", AttributeValue.fromL(newIdAttrs),
                ":empty_list", AttributeValue.fromL(List.of()) // Empty list if attribute doesn't exist
        );

        Map<String, String> expressionAttributeNames = Map.of(
                "#attr", SENT_ATTRIBUTE
        );

        // Create the update request
        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName(savedQueriesTable)
                .key(Map.of(PRIMARY_KEY_ATTRIBUTE, AttributeValue.fromS(primaryKeyValue)))
                .updateExpression(updateExpression)
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        // Perform the update operation
        dynamoDbClient.updateItem(updateRequest);
        System.out.println("Successfully updated the item.");
    }

    private void sendEmail(SesClient sesClient, String sender, String recipient, String subject, String bodyText, String bodyHtml) {
        
        if(bodyText.isEmpty()) {
            log.info("Nothing to send");
            return;
        }

        Content subjectContent = Content.builder().data(subject).build();
        Body body = Body.builder()
                .text(Content.builder().data(bodyText).build())
                // .html(Content.builder().data(bodyHtml).build())
                .build();

        Message message = Message.builder()
                .subject(subjectContent)
                .body(body)
                .build();

        SendEmailRequest request = SendEmailRequest.builder()
                .destination(Destination.builder().toAddresses(recipient).build())
                .message(message)
                .source(sender)
                .build();

        sesClient.sendEmail(request);
    }


    public static void main(String[] args) {

    }
}
