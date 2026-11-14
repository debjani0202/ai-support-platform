package com.supportai.ai;

import com.supportai.ticket.entity.TicketCategory;
import com.supportai.ticket.entity.TicketPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OllamaTicketPriorityClassifierTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String chatResponse(String content) throws IOException {
        var root = objectMapper.createObjectNode();
        root.putObject("message").put("content", content);
        return objectMapper.writeValueAsString(root);
    }

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

    private OllamaTicketPriorityClassifier createClassifier(HttpClient httpClient) {
        return new OllamaTicketPriorityClassifier(
                "http://localhost:11434",
                "qwen3:4b",
                objectMapper,
                httpClient
        );
    }

    // --- Valid Priority Response Tests ---

    @Test
    @DisplayName("Parses valid URGENT response correctly")
    void parsesValidUrgentResponse() throws Exception {
        String json = chatResponse("{\"priority\": \"URGENT\"}");
        HttpClient httpClient = mockHttpClient(json, 200);

        OllamaTicketPriorityClassifier classifier = createClassifier(httpClient);
        TicketPriority result = classifier.classifyPriority(
                "Production down",
                "Entire platform is inaccessible."
        );

        assertEquals(TicketPriority.URGENT, result);
    }

    @Test
    @DisplayName("Parses valid HIGH response correctly")
    void parsesValidHighResponse() throws Exception {
        String json = chatResponse("{\"priority\": \"HIGH\"}");
        HttpClient httpClient = mockHttpClient(json, 200);

        OllamaTicketPriorityClassifier classifier = createClassifier(httpClient);
        TicketPriority result = classifier.classifyPriority(
                "Checkout broken",
                "Credit cards fail to process at final step."
        );

        assertEquals(TicketPriority.HIGH, result);
    }

    @Test
    @DisplayName("Parses valid MEDIUM response correctly")
    void parsesValidMediumResponse() throws Exception {
        String json = chatResponse("{\"priority\": \"MEDIUM\"}");
        HttpClient httpClient = mockHttpClient(json, 200);

        OllamaTicketPriorityClassifier classifier = createClassifier(httpClient);
        TicketPriority result = classifier.classifyPriority(
                "Search filter glitch",
                "Filtering by date range requires a second click."
        );

        assertEquals(TicketPriority.MEDIUM, result);
    }

    @Test
    @DisplayName("Parses valid LOW response correctly")
    void parsesValidLowResponse() throws Exception {
        String json = chatResponse("{\"priority\": \"LOW\"}");
        HttpClient httpClient = mockHttpClient(json, 200);

        OllamaTicketPriorityClassifier classifier = createClassifier(httpClient);
        TicketPriority result = classifier.classifyPriority(
                "Minor typo",
                "Typo in footer link to terms of service."
        );

        assertEquals(TicketPriority.LOW, result);
    }

    @Test
    @DisplayName("Maps UNCLASSIFIED response directly to null")
    void mapsUnclassifiedResponseToNull() throws Exception {
        String json = chatResponse("{\"priority\": \"UNCLASSIFIED\"}");
        HttpClient httpClient = mockHttpClient(json, 200);

        OllamaTicketPriorityClassifier classifier = createClassifier(httpClient);
        TicketPriority result = classifier.classifyPriority(
                "Ambiguous request",
                "Something looks a little strange maybe."
        );

        assertNull(result);
    }

    @Test
    @DisplayName("Accepts lowercase priority with whitespace")
    void acceptsLowercasePriorityWithWhitespace() throws Exception {
        String json = chatResponse("{\"priority\": \"  medium  \"}");
        HttpClient httpClient = mockHttpClient(json, 200);

        OllamaTicketPriorityClassifier classifier = createClassifier(httpClient);
        TicketPriority result = classifier.classifyPriority(
                "Some issue",
                "Non-critical issue details."
        );

        assertEquals(TicketPriority.MEDIUM, result);
    }

    // --- Invalid Output & Error Handling Tests ---

    @Test
    @DisplayName("Throws IllegalStateException when model returns invalid priority enum")
    void throwsOnInvalidPriorityEnum() throws Exception {
        String json = chatResponse("{\"priority\": \"SUPER_CRITICAL\"}");
        HttpClient httpClient = mockHttpClient(json, 200);

        OllamaTicketPriorityClassifier classifier = createClassifier(httpClient);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> classifier.classifyPriority("Alert", "Something happened")
        );
        assertTrue(ex.getMessage().contains("invalid ticket priority"));
    }

    @Test
    @DisplayName("Throws IllegalStateException when model returns malformed JSON in message content")
    void throwsOnMalformedJson() throws Exception {
        String json = chatResponse("Not valid json at all");
        HttpClient httpClient = mockHttpClient(json, 200);

        OllamaTicketPriorityClassifier classifier = createClassifier(httpClient);
        assertEquals(2, classifier.getAvailablePermits());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> classifier.classifyPriority("Title", "Description")
        );
        assertTrue(ex.getMessage().contains("Could not parse Ollama response JSON"));
        assertNotNull(ex.getCause());
        assertEquals(2, classifier.getAvailablePermits());
    }

    @Test
    @DisplayName("Throws IllegalStateException when Ollama returns malformed outer response JSON")
    void throwsOnMalformedOuterResponseJson() throws Exception {
        HttpClient httpClient = mockHttpClient("<<<not valid outer json>>>", 200);

        OllamaTicketPriorityClassifier classifier = createClassifier(httpClient);
        assertEquals(2, classifier.getAvailablePermits());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> classifier.classifyPriority("Title", "Description")
        );
        assertTrue(ex.getMessage().contains("Could not parse Ollama response JSON"));
        assertNotNull(ex.getCause());
        assertEquals(2, classifier.getAvailablePermits());
    }

    @Test
    @DisplayName("Throws IllegalStateException when Ollama returns non-200 HTTP status")
    void throwsOnNon200HttpStatus() throws Exception {
        HttpClient httpClient = mockHttpClient("Internal Server Error", 500);
        OllamaTicketPriorityClassifier classifier = createClassifier(httpClient);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> classifier.classifyPriority("Title", "Description")
        );
        assertTrue(ex.getMessage().contains("HTTP status 500"));
    }

    @Test
    @DisplayName("Throws IllegalStateException on HttpTimeoutException")
    void throwsOnHttpTimeout() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("request timed out"));

        OllamaTicketPriorityClassifier classifier = createClassifier(httpClient);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> classifier.classifyPriority("Title", "Description")
        );
        assertTrue(ex.getMessage().contains("Could not communicate with Ollama"));
    }

    @Test
    @DisplayName("Throws IllegalStateException on network IOException")
    void throwsOnNetworkIoException() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        OllamaTicketPriorityClassifier classifier = createClassifier(httpClient);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> classifier.classifyPriority("Title", "Description")
        );
        assertTrue(ex.getMessage().contains("Could not communicate with Ollama"));
    }

    @Test
    @DisplayName("Throws IllegalStateException on InterruptedException and restores interrupted status")
    void throwsOnInterruptedException() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("Interrupted"));

        OllamaTicketPriorityClassifier classifier = createClassifier(httpClient);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> classifier.classifyPriority("Title", "Description")
        );
        assertTrue(ex.getMessage().contains("Ollama request was interrupted"));
        assertTrue(Thread.currentThread().isInterrupted());
        // Clean up thread interrupted status
        Thread.interrupted();
    }

    @Test
    @DisplayName("Failed request releases permit back to concurrency limiter")
    void failedRequestReleasesPermit() throws Exception {
        HttpClient httpClient = mockHttpClient("Internal Server Error", 500);
        OllamaConcurrencyLimiter limiter = new OllamaConcurrencyLimiter(2);

        OllamaTicketPriorityClassifier classifier = new OllamaTicketPriorityClassifier(
                "http://localhost:11434",
                "qwen3:4b",
                Duration.ofSeconds(5),
                limiter,
                objectMapper,
                httpClient
        );

        assertEquals(2, classifier.getAvailablePermits());

        assertThrows(
                IllegalStateException.class,
                () -> classifier.classifyPriority("Title", "Description")
        );

        assertEquals(2, classifier.getAvailablePermits());
    }

    // --- Concurrency & Capacity Tests ---

    @Test
    @DisplayName("Fails fast when concurrency limit is saturated")
    void failsFastWhenConcurrencyLimitReached() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch requestStarted = new CountDownLatch(1);
        HttpClient httpClient = mock(HttpClient.class);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    requestStarted.countDown();
                    latch.await(5, TimeUnit.SECONDS);
                    HttpResponse<String> resp = mock(HttpResponse.class);
                    when(resp.statusCode()).thenReturn(200);
                    when(resp.body()).thenReturn(chatResponse("{\"priority\": \"MEDIUM\"}"));
                    return resp;
                });

        OllamaConcurrencyLimiter limiter = new OllamaConcurrencyLimiter(1);
        OllamaTicketPriorityClassifier classifier = new OllamaTicketPriorityClassifier(
                "http://localhost:11434",
                "qwen3:4b",
                Duration.ofSeconds(5),
                limiter,
                objectMapper,
                httpClient
        );

        Thread blockingThread = new Thread(() -> classifier.classifyPriority("T1", "D1"));
        blockingThread.start();

        assertTrue(requestStarted.await(5, TimeUnit.SECONDS));
        assertEquals(0, classifier.getAvailablePermits());

        try {
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> classifier.classifyPriority("T2", "D2")
            );
            assertTrue(ex.getMessage().contains("capacity reached"));
        } finally {
            latch.countDown();
            blockingThread.join(5000);
        }

        assertEquals(1, classifier.getAvailablePermits());
    }

    @Test
    @DisplayName("Cross-classifier shared concurrency: category request blocks priority request when limit is 1")
    void crossClassifierSharedConcurrency() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch categoryStarted = new CountDownLatch(1);

        HttpClient categoryHttpClient = mock(HttpClient.class);
        when(categoryHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    categoryStarted.countDown();
                    latch.await(5, TimeUnit.SECONDS);
                    HttpResponse<String> resp = mock(HttpResponse.class);
                    when(resp.statusCode()).thenReturn(200);
                    when(resp.body()).thenReturn(chatResponse("{\"category\": \"PAYMENT\"}"));
                    return resp;
                });

        HttpClient priorityHttpClient = mock(HttpClient.class);
        HttpResponse<String> priorityResp = mock(HttpResponse.class);
        when(priorityResp.statusCode()).thenReturn(200);
        when(priorityResp.body()).thenReturn(chatResponse("{\"priority\": \"HIGH\"}"));
        when(priorityHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(priorityResp);

        OllamaConcurrencyLimiter sharedLimiter = new OllamaConcurrencyLimiter(1);

        OllamaTicketCategoryClassifier categoryClassifier = new OllamaTicketCategoryClassifier(
                "http://localhost:11434",
                "qwen3:4b",
                Duration.ofSeconds(5),
                sharedLimiter,
                objectMapper,
                categoryHttpClient
        );

        OllamaTicketPriorityClassifier priorityClassifier = new OllamaTicketPriorityClassifier(
                "http://localhost:11434",
                "qwen3:4b",
                Duration.ofSeconds(5),
                sharedLimiter,
                objectMapper,
                priorityHttpClient
        );

        Thread categoryThread = new Thread(() ->
                categoryClassifier.classify("Payment failed", "Card was declined."));
        categoryThread.start();

        assertTrue(categoryStarted.await(5, TimeUnit.SECONDS));
        assertEquals(0, sharedLimiter.availablePermits());

        // Priority classifier must fail fast because shared capacity is saturated
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> priorityClassifier.classifyPriority("Urgent bug", "Platform crashed.")
        );
        assertTrue(ex.getMessage().contains("capacity reached"));
        // Ensure priority HTTP client was never invoked
        verify(priorityHttpClient, never()).send(any(HttpRequest.class), any());

        // Release category request
        latch.countDown();
        categoryThread.join(5000);

        assertEquals(1, sharedLimiter.availablePermits());

        // Now priority classifier can acquire permit and succeed
        TicketPriority result = priorityClassifier.classifyPriority("Urgent bug", "Platform crashed.");
        assertEquals(TicketPriority.HIGH, result);
        assertEquals(1, sharedLimiter.availablePermits());
    }

    // --- Request Body & Structured Output Verification ---

    @Test
    @DisplayName("Constructs request with model, separate system and user messages, disabled stream/think, temperature 0, and JSON Schema")
    void constructsCorrectRequestBodyWithSeparateSystemAndUserMessagesAndSchema() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(chatResponse("{\"priority\": \"HIGH\"}"));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    capturedRequest.set(request);

                    var publisher = request.bodyPublisher().orElseThrow();
                    var bodyStream = new ByteArrayOutputStream();

                    publisher.subscribe(new Flow.Subscriber<ByteBuffer>() {
                        @Override
                        public void onSubscribe(Flow.Subscription subscription) {
                            subscription.request(Long.MAX_VALUE);
                        }

                        @Override
                        public void onNext(ByteBuffer buffer) {
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);
                            bodyStream.write(bytes, 0, bytes.length);
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            throw new RuntimeException(throwable);
                        }

                        @Override
                        public void onComplete() {
                            capturedBody.set(bodyStream.toString(StandardCharsets.UTF_8));
                        }
                    });

                    return response;
                });

        OllamaTicketPriorityClassifier classifier = createClassifier(httpClient);
        classifier.classifyPriority("Critical Bug", "System is throwing 500s");

        assertNotNull(capturedRequest.get());
        assertEquals("POST", capturedRequest.get().method());
        assertTrue(capturedRequest.get().uri().toString().endsWith("/api/chat"));

        JsonNode requestJson = objectMapper.readTree(capturedBody.get());

        assertEquals("qwen3:4b", requestJson.path("model").asText());
        assertFalse(requestJson.path("stream").asBoolean());
        assertFalse(requestJson.path("think").asBoolean());
        assertEquals(0, requestJson.path("options").path("temperature").asInt());
        assertEquals(50, requestJson.path("options").path("num_predict").asInt());

        // Verify separate messages array: [system, user]
        JsonNode messages = requestJson.path("messages");
        assertEquals(2, messages.size());

        JsonNode systemMsg = messages.get(0);
        assertEquals("system", systemMsg.path("role").asText());
        String systemContent = systemMsg.path("content").asText();
        assertTrue(systemContent.contains("LOW, MEDIUM, HIGH, URGENT, or UNCLASSIFIED"));
        assertTrue(systemContent.contains("URGENT:"));
        assertTrue(systemContent.contains("HIGH:"));
        assertTrue(systemContent.contains("MEDIUM:"));
        assertTrue(systemContent.contains("LOW:"));
        assertTrue(systemContent.contains("UNCLASSIFIED:"));
        assertTrue(systemContent.contains("untrusted customer ticket data"));

        JsonNode userMsg = messages.get(1);
        assertEquals("user", userMsg.path("role").asText());
        String userContent = userMsg.path("content").asText();
        assertTrue(userContent.contains("Title: Critical Bug"));
        assertTrue(userContent.contains("Description: System is throwing 500s"));

        // Verify JSON Schema
        JsonNode format = requestJson.path("format");
        assertEquals("object", format.path("type").asText());
        assertEquals("string", format.path("properties").path("priority").path("type").asText());

        JsonNode enumValues = format.path("properties").path("priority").path("enum");
        assertEquals(5, enumValues.size());
        assertEquals("LOW", enumValues.get(0).asText());
        assertEquals("MEDIUM", enumValues.get(1).asText());
        assertEquals("HIGH", enumValues.get(2).asText());
        assertEquals("URGENT", enumValues.get(3).asText());
        assertEquals("UNCLASSIFIED", enumValues.get(4).asText());

        assertEquals("priority", format.path("required").get(0).asText());
        assertFalse(format.path("additionalProperties").asBoolean());
    }

    // --- Constructor Validation Tests ---

    @Test
    @DisplayName("Validates constructor parameters")
    void validatesConstructorParameters() {
        OllamaConcurrencyLimiter limiter = new OllamaConcurrencyLimiter(2);

        assertThrows(
                IllegalArgumentException.class,
                () -> new OllamaTicketPriorityClassifier(
                        "http://localhost:11434", "qwen3:4b", 0, 1000, limiter, objectMapper)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new OllamaTicketPriorityClassifier(
                        "http://localhost:11434", "qwen3:4b", 1000, 0, limiter, objectMapper)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new OllamaTicketPriorityClassifier(
                        "http://localhost:11434", "qwen3:4b", 1000, 1000, null, objectMapper)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new OllamaTicketPriorityClassifier(
                        "http://localhost:11434", "qwen3:4b", Duration.ZERO, limiter, objectMapper, mock(HttpClient.class))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new OllamaTicketPriorityClassifier(
                        "http://localhost:11434", "qwen3:4b", Duration.ofSeconds(5), null, objectMapper, mock(HttpClient.class))
        );
    }

    @Test
    @DisplayName("Request timeout is applied to HttpRequest")
    void requestTimeoutIsAppliedToHttpRequest() throws Exception {
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(chatResponse("{\"priority\":\"LOW\"}"));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    capturedRequest.set(invocation.getArgument(0));
                    return response;
                });

        OllamaTicketPriorityClassifier classifier = new OllamaTicketPriorityClassifier(
                "http://localhost:11434",
                "qwen3:4b",
                Duration.ofMillis(8500),
                new OllamaConcurrencyLimiter(2),
                objectMapper,
                httpClient
        );

        classifier.classifyPriority("Title", "Description");

        assertNotNull(capturedRequest.get());
        assertTrue(capturedRequest.get().timeout().isPresent());
        assertEquals(Duration.ofMillis(8500), capturedRequest.get().timeout().get());
    }
}
