
package com.supportai.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.supportai.ticket.entity.TicketCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

@Component
public class OllamaTicketCategoryClassifier implements TicketCategoryClassifier {

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Constructor used by Spring Boot.
    @Autowired
    public OllamaTicketCategoryClassifier(
            @Value("${app.ai.ollama.base-url}") String baseUrl,
            @Value("${app.ai.ollama.model}") String model,
            ObjectMapper objectMapper) {

        this(
                baseUrl,
                model,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build()
        );
    }

    // Constructor for unit tests to provide a custom HTTP client.
    OllamaTicketCategoryClassifier(
            String baseUrl,
            String model,
            ObjectMapper objectMapper,
            HttpClient httpClient) {

        this.baseUrl = baseUrl;
        this.model = model;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public TicketCategory classify(String title, String description) {
        try {
            String prompt = """
                    You are a support ticket classification system.
                    Choose exactly one category based on the main problem described.

                    Category definitions:
                    - ACCOUNT_ACCESS: Login failures, passwords, account lockouts, or authentication problems.
                    - PAYMENT: Payments, billing, refunds, invoices, or subscription charges.
                    - BUG_REPORT: Software defects, crashes, unexpected behavior, or broken features.
                    - TECHNICAL_ISSUE: Technical difficulties where a software defect is not clearly indicated.
                    - FEATURE_REQUEST: Requests for new functionality or improvements.
                    - GENERAL_QUERY: General questions that do not fit the other categories.

                    Important rules:
                    - A software crash or reproducible unexpected application behavior should be BUG_REPORT.
                    - Do not choose ACCOUNT_ACCESS unless the main problem concerns login, authentication, passwords, or account access.
                    - Classify the problem described, not just individual words.
                    - Return only the category name.

                    Title: %s
                    Description: %s
                    """.formatted(title, description);

            var requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);

            var messages = objectMapper.createArrayNode();
            var message = objectMapper.createObjectNode();
            message.put("role", "user");
            message.put("content", prompt);
            messages.add(message);

            requestBody.set("messages", messages);
            requestBody.put("stream", false);
            requestBody.put("think", false);

            var categories = objectMapper.createArrayNode();
            categories.add("ACCOUNT_ACCESS");
            categories.add("PAYMENT");
            categories.add("TECHNICAL_ISSUE");
            categories.add("BUG_REPORT");
            categories.add("FEATURE_REQUEST");
            categories.add("GENERAL_QUERY");

            var format = objectMapper.createObjectNode();
            format.put("type", "string");
            format.set("enum", categories);
            requestBody.set("format", format);

            var options = objectMapper.createObjectNode();
            options.put("num_predict", 30);
            options.put("temperature", 0);
            requestBody.set("options", options);

            String requestJson =
                    objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            baseUrl.replaceAll("/+$", "") + "/api/chat"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Ollama returned HTTP status "
                                + response.statusCode());
            }

            JsonNode responseJson =
                    objectMapper.readTree(response.body());

            String categoryText =
                    responseJson.path("message").path("content").asText("").trim();

            if (categoryText.length() >= 2
                    && categoryText.startsWith("\"")
                    && categoryText.endsWith("\"")) {
                categoryText = categoryText.substring(
                        1, categoryText.length() - 1).trim();
            }

            try {
                return TicketCategory.valueOf(
                        categoryText.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "Ollama returned an invalid ticket category");
            }

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not communicate with Ollama", exception);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Ollama request was interrupted", exception);

        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Invalid Ollama URL or response format", exception);
        }
    }
}
