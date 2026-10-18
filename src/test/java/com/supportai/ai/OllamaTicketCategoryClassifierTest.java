package com.supportai.ai;

import com.supportai.ticket.entity.TicketCategory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OllamaTicketCategoryClassifierTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private HttpClient mockHttpClient(String responseBody, int statusCode)
            throws IOException, InterruptedException {

        HttpClient httpClient = mock(HttpClient.class);

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(responseBody);

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenReturn(response);

        return httpClient;
    }

    @Test
    void classifyReturnsCategoryFromValidResponse()
            throws IOException, InterruptedException {

        HttpClient httpClient = mockHttpClient(
                """
                {"response":"ACCOUNT_ACCESS"}
                """,
                200
        );

        OllamaTicketCategoryClassifier classifier =
                new OllamaTicketCategoryClassifier(
                        "http://localhost:11434",
                        "qwen3:4b",
                        objectMapper,
                        httpClient
                );

        TicketCategory result = classifier.classify(
                "Cannot log in",
                "I cannot access my account."
        );

        assertEquals(TicketCategory.ACCOUNT_ACCESS, result);
    }

    @Test
    void classifyAcceptsLowercaseCategoryWithWhitespace()
            throws IOException, InterruptedException {

        HttpClient httpClient = mockHttpClient(
                """
                {"response":" payment "}
                """,
                200
        );

        OllamaTicketCategoryClassifier classifier =
                new OllamaTicketCategoryClassifier(
                        "http://localhost:11434",
                        "qwen3:4b",
                        objectMapper,
                        httpClient
                );

        TicketCategory result = classifier.classify(
                "Payment problem",
                "My payment failed."
        );

        assertEquals(TicketCategory.PAYMENT, result);
    }

    @Test
    void classifyRejectsInvalidCategory()
            throws IOException, InterruptedException {

        HttpClient httpClient = mockHttpClient(
                """
                {"response":"UNKNOWN_CATEGORY"}
                """,
                200
        );

        OllamaTicketCategoryClassifier classifier =
                new OllamaTicketCategoryClassifier(
                        "http://localhost:11434",
                        "qwen3:4b",
                        objectMapper,
                        httpClient
                );

        assertThrows(
                IllegalStateException.class,
                () -> classifier.classify(
                        "Login problem",
                        "I cannot log in."
                )
        );
    }

    @Test
    void classifyRejectsNonSuccessfulHttpStatus()
            throws IOException, InterruptedException {

        HttpClient httpClient = mockHttpClient(
                """
                {"error":"model unavailable"}
                """,
                500
        );

        OllamaTicketCategoryClassifier classifier =
                new OllamaTicketCategoryClassifier(
                        "http://localhost:11434",
                        "qwen3:4b",
                        objectMapper,
                        httpClient
                );

        assertThrows(
                IllegalStateException.class,
                () -> classifier.classify(
                        "Payment problem",
                        "My payment failed."
                )
        );
    }
}
