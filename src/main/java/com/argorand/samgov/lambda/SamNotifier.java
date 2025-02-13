package com.argorand.samgov.lambda;

import java.util.HashSet;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.argorand.samgov.beans.ApiResponse;
import com.argorand.samgov.beans.Result;
import com.argorand.samgov.beans.dynamodb.SamQuery;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
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

    private ObjectMapper objectMapper = new ObjectMapper();

    {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    private HttpClient client = HttpClient.newHttpClient();

    private final Logger log = LoggerFactory.getLogger(SamNotifier.class);

    @Bean
    public DynamoDbEnhancedClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (awsEndpoint != null && !awsEndpoint.isEmpty()) {
            builder.endpointOverride(java.net.URI.create(awsEndpoint));
        }
        DynamoDbClient regularClient = builder.build();
        return DynamoDbEnhancedClient.builder().dynamoDbClient(regularClient).build();
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
    public Supplier<Void> checkQueryUpdates(DynamoDbEnhancedClient dynamoDbClient, SesClient sesClient) {
        return () -> {
            DynamoDbTable<SamQuery> table = dynamoDbClient.table(savedQueriesTable, TableSchema.fromBean(SamQuery.class));

            for(Page<SamQuery> page: table.scan(ScanEnhancedRequest.builder().build())) {
                for (SamQuery userQuery : page.items()) {
                    try {
                        var preparedUrl = DateSubstitutor.updateUrl(userQuery.getQueryUrl());
                        
                        log.info("Final URL: {}", preparedUrl);
                        HttpRequest request = RestRequestFactory.buildMainRestQuery(preparedUrl);
            
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        ApiResponse apiResponse = objectMapper.readValue(response.body(), ApiResponse.class);

                        if(apiResponse.getEmbedded().getResults().isEmpty()) {
                            log.info("No search results");
                        } else {
                            var alreadyProcessedIds = 
                                Optional.ofNullable(userQuery.getProcessedOpportunities()).orElse(new HashSet<String>());
                            log.info(alreadyProcessedIds.toString());
                            // log.info("alreadyProcessedIds {}", alreadyProcessedIds.toString());
                            // log.info("Results before cleanup {}", apiResponse.getEmbedded().getResults().size());
                            apiResponse.getEmbedded().getResults().removeIf(r -> alreadyProcessedIds.contains(r.getId()));
                            // log.info("Results after cleanup {}", apiResponse.getEmbedded().getResults().size());
                            if(!apiResponse.getEmbedded().getResults().isEmpty()) {
                                var subjectLine = 
                                    String.format("Your SAM.gov query %s has new results", 
                                        Optional.ofNullable(userQuery.getQueryDescription()).orElse(SamUtils.describeUrl(preparedUrl))
                                );
                                sendEmail(sesClient, senderEmailAddress, userQuery.getEmail(), subjectLine, 
                                    null, SamUtils.generateSummary(apiResponse, client, objectMapper));
                                var opportunityIds = apiResponse.getEmbedded().getResults().stream().map(Result::getId).collect(Collectors.toList());
                                userQuery.addProcessedOpportunities(opportunityIds);
                                table.updateItem(userQuery);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return null;
        };
    }

    private void sendEmail(SesClient sesClient, String sender, String recipient, String subject, String bodyText, String bodyHtml) {
        
        Content subjectContent = Content.builder().data(subject).build();
        Body body = Body.builder()
                //.text(Content.builder().data(bodyText).build())
                .html(Content.builder().data(bodyHtml).build())
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
