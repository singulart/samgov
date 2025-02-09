package com.argorand.samgov.lambda;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.argorand.samgov.beans.ApiResponse;
import com.argorand.samgov.beans.Description;
import com.argorand.samgov.beans.Organization;
import com.argorand.samgov.beans.Result;

public class SamUtils {
    
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
            }
        }

        // Build human-readable description
        return queryMap.entrySet().stream()
                .map(entry -> parseParameter(entry.getKey(), entry.getValue()))
                .filter(desc -> !desc.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    public static String parseParameter(String key, String value) {
        switch (key) {
            case "q":
                return "Keyword: " + value;
            case "response_date.to":
                return "Response deadline (to): " + value;
            case "response_date.from":
                return "Response deadline (from): " + value;
            case "naics":
                return "NAICS code(s): " + value;
            case "psc":
                return "PSC code(s): " + value.replace(",", ", ");
            case "organization_id":
                return "Org ID: " + value;
            case "vendor_name":
                return "Vendor: " + value;
            case "ueiSAM":
                return "UEI SAM: " + value;
            case "notice_type":
                return "Notice type(s): " + value;
            case "set_aside":
                return "Set-Aside: " + value;
            default:
                return ""; // Skip unknown parameters
        }
    }

    public static String generateSummary(ApiResponse apiResponse) {
        StringBuilder summary = new StringBuilder();

        List<Result> results = apiResponse.getEmbedded().getResults();

        for (Result result : results) {

            summary.append("View on SAM: ").append(
                String.format("https://sam.gov/opp/%s/view", result.getId())).append("\n");
            summary.append("Title: ").append(result.getTitle()).append("\n");

            if (result.getDescriptions() != null) {
                for (Description description : result.getDescriptions()) {
                    summary.append("Description: ").append(description.getContent()).append("\n");
                }
            }

            // Add Organization.name for level = 1
            if (result.getOrganizationHierarchy() != null) {
                for (Organization organization : result.getOrganizationHierarchy()) {
                    if (organization.getLevel() == 1) {
                        summary.append("Organization (Level 1): ").append(organization.getName()).append("\n");
                    }
                }
            }

            // Separate results with a divider for clarity
            summary.append("\n---\n");
        }

        return summary.toString();
    }        
}
