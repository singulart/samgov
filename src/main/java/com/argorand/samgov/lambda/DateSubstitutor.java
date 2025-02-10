package com.argorand.samgov.lambda;

import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class DateSubstitutor {

    public static String updateUrl(String inputUrl) {
        try {

            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("US/Eastern"));

            // DateTime formatter for the desired format
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'-00:00'");
            String modifiedDateFrom = now.format(formatter);
            //String modifiedDateTo = URLEncoder.encode(now.format(formatter), StandardCharsets.UTF_8);

            // Parse the input URL
            URI uri = new URI(inputUrl);
            String query = uri.getQuery();

            // Split the query into key-value pairs
            Map<String, String> queryParams = Arrays.stream(query.split("&"))
                .map(param -> param.split("=", 2))
                .collect(Collectors.toMap(
                    pair -> pair[0],
                    pair -> pair.length > 1 ? pair[1] : "",
                    (existing, replacement) -> existing,
                    LinkedHashMap::new
                ));

            // Substitute `modified_date` parameters
            queryParams.put("modified_date.from", modifiedDateFrom);
            queryParams.put("modified_date.to", "");

            // Reconstruct the query string
            String updatedQuery = queryParams.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

            // Build the updated URL
            URI updatedUri = new URI(
                uri.getScheme(),
                uri.getAuthority(),
                uri.getPath(),
                updatedQuery,
                uri.getFragment()
            );

            return updatedUri.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error updating URL", e);
        }
    }    
}
