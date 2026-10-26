package com.supportai.ai;

import com.supportai.ticket.entity.TicketCategory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.util.concurrent.atomic.AtomicReference;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OllamaTicketCategoryClassifierTest {

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

    private OllamaTicketCategoryClassifier createClassifier(
            HttpClient httpClient) {
        return new OllamaTicketCategoryClassifier(
                "http://localhost:11434",
                "qwen3:4b",
                objectMapper,
                httpClient
        );
    }

    @Test
    void classifyReturnsCategoryFromChatResponse()
            throws IOException, InterruptedException {

        HttpClient httpClient = mockHttpClient(
                chatResponse("{\"category\":\"ACCOUNT_ACCESS\"}"), 200);

        TicketCategory result = createClassifier(httpClient).classify(
                "Cannot log in",
                "I cannot access my account."
        );

        assertEquals(TicketCategory.ACCOUNT_ACCESS, result);
    }

    @Test
    void classifyAcceptsLowercaseCategoryWithWhitespace()
            throws IOException, InterruptedException {

        HttpClient httpClient = mockHttpClient(
                chatResponse("{\"category\":\" payment \"}"), 200);

        TicketCategory result = createClassifier(httpClient).classify(
                "Payment problem",
                "My payment failed."
        );

        assertEquals(TicketCategory.PAYMENT, result);
    }

    @Test
    void classifyAcceptsJsonCategory()
            throws IOException, InterruptedException {

        HttpClient httpClient = mockHttpClient(
                chatResponse("{\"category\":\"ACCOUNT_ACCESS\"}"), 200);

        TicketCategory result = createClassifier(httpClient).classify(
                "Cannot log in",
                "My login attempt keeps failing."
        );

        assertEquals(TicketCategory.ACCOUNT_ACCESS, result);
    }

    @Test
    void classifyRejectsInvalidCategory()
            throws IOException, InterruptedException {

        HttpClient httpClient = mockHttpClient(
                chatResponse("{\"category\":\"UNKNOWN_CATEGORY\"}"), 200);

        assertThrows(
                IllegalStateException.class,
                () -> createClassifier(httpClient).classify(
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

        assertThrows(
                IllegalStateException.class,
                () -> createClassifier(httpClient).classify(
                        "Payment problem",
                        "My payment failed."
                )
        );
    }

    @Test
    void classifySendsConstrainedOutputAndFastGenerationOptions()
            throws Exception {

        AtomicReference<String> capturedBody = new AtomicReference<>();

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(chatResponse("{\"category\":\"BUG_REPORT\"}"));

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)
        )).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            var publisher = request.bodyPublisher().orElseThrow();
            var body = new java.io.ByteArrayOutputStream();

            publisher.subscribe(
                    new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
                @Override
                public void onSubscribe(
                        java.util.concurrent.Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(java.nio.ByteBuffer buffer) {
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    body.write(bytes, 0, bytes.length);
                }

                @Override
                public void onError(Throwable throwable) {
                    throw new RuntimeException(throwable);
                }

                @Override
                public void onComplete() {
                    capturedBody.set(body.toString(
                            java.nio.charset.StandardCharsets.UTF_8));
                }
            });

            return response;
        });

        TicketCategory result = createClassifier(httpClient).classify(
                "Application crashes",
                "The app crashes during upload."
        );

        assertEquals(TicketCategory.BUG_REPORT, result);

        JsonNode requestJson = objectMapper.readTree(capturedBody.get());

        assertEquals(false, requestJson.path("think").asBoolean());
        assertEquals(50, requestJson.path("options")
                .path("num_predict").asInt());
        assertEquals(0, requestJson.path("options")
                .path("temperature").asInt());
        assertEquals("object", requestJson.path("format")
                .path("type").asText());
        assertEquals("string", requestJson.path("format")
                .path("properties").path("category")
                .path("type").asText());
        assertEquals(6, requestJson.path("format")
                .path("properties").path("category")
                .path("enum").size());

        String prompt = requestJson.path("messages")
                .get(0).path("content").asText();

        assertTrue(prompt.contains(
                "If the application crashes or produces an incorrect calculation or result, choose BUG_REPORT."));
        assertTrue(prompt.contains(
                "If a payment transaction is duplicated, fails, or is processed incorrectly, choose PAYMENT."));
        assertTrue(prompt.contains(
                "Do not choose PAYMENT merely because a ticket mentions an invoice, bill, or amount."));
        assertTrue(prompt.contains(
                "choose TECHNICAL_ISSUE unless a specific software defect is described."));
    }

    @Test
    void invalidConfigurationValuesThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                new OllamaTicketCategoryClassifier("http://localhost:11434", "qwen3:4b", 0, 10000, 2, objectMapper));
        assertThrows(IllegalArgumentException.class, () ->
                new OllamaTicketCategoryClassifier("http://localhost:11434", "qwen3:4b", -1, 10000, 2, objectMapper));

        assertThrows(IllegalArgumentException.class, () ->
                new OllamaTicketCategoryClassifier("http://localhost:11434", "qwen3:4b", 3000, 0, 2, objectMapper));
        assertThrows(IllegalArgumentException.class, () ->
                new OllamaTicketCategoryClassifier("http://localhost:11434", "qwen3:4b", 3000, -1, 2, objectMapper));

        assertThrows(IllegalArgumentException.class, () ->
                new OllamaTicketCategoryClassifier("http://localhost:11434", "qwen3:4b", 3000, 10000, 0, objectMapper));
        assertThrows(IllegalArgumentException.class, () ->
                new OllamaTicketCategoryClassifier("http://localhost:11434", "qwen3:4b", 3000, 10000, -1, objectMapper));
    }

    @Test
    void configuredTimeoutAndConcurrencyApplied() {
        OllamaTicketCategoryClassifier classifier = new OllamaTicketCategoryClassifier(
                "http://localhost:11434",
                "qwen3:4b",
                2500,
                8000,
                3,
                objectMapper
        );

        assertEquals(Duration.ofMillis(8000), classifier.getRequestTimeout());
        assertEquals(3, classifier.getAvailablePermits());
    }

    @Test
    void successfulClassificationReleasesConcurrencyPermit()
            throws IOException, InterruptedException {
        HttpClient httpClient = mockHttpClient(
                chatResponse("{\"category\":\"BUG_REPORT\"}"), 200);

        OllamaTicketCategoryClassifier classifier = createClassifier(httpClient);
        assertEquals(2, classifier.getAvailablePermits());

        TicketCategory result = classifier.classify("Crash", "App crashes on launch.");

        assertEquals(TicketCategory.BUG_REPORT, result);
        assertEquals(2, classifier.getAvailablePermits());
    }

    @Test
    void timedOutRequestReleasesPermitAndThrowsIllegalStateException()
            throws IOException, InterruptedException {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("Request timed out"));

        OllamaTicketCategoryClassifier classifier = createClassifier(httpClient);
        assertEquals(2, classifier.getAvailablePermits());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> classifier.classify("Slow response", "Timeout expected.")
        );

        assertEquals("Could not communicate with Ollama", ex.getMessage());
        assertTrue(ex.getCause() instanceof HttpTimeoutException);
        assertEquals(2, classifier.getAvailablePermits());
    }

    @Test
    void failedRequestReleasesPermit()
            throws IOException, InterruptedException {
        HttpClient httpClient = mockHttpClient("{\"error\":\"internal error\"}", 500);

        OllamaTicketCategoryClassifier classifier = createClassifier(httpClient);
        assertEquals(2, classifier.getAvailablePermits());

        assertThrows(
                IllegalStateException.class,
                () -> classifier.classify("Error", "Error expected.")
        );

        assertEquals(2, classifier.getAvailablePermits());
    }

    @Test
    void concurrentRequestsBeyondLimitFailFast() throws Exception {
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseRequest = new CountDownLatch(1);

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(chatResponse("{\"category\":\"PAYMENT\"}"));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    requestStarted.countDown();
                    releaseRequest.await(5, TimeUnit.SECONDS);
                    return response;
                });

        OllamaTicketCategoryClassifier classifier = new OllamaTicketCategoryClassifier(
                "http://localhost:11434",
                "qwen3:4b",
                Duration.ofMillis(5000),
                new Semaphore(1),
                objectMapper,
                httpClient
        );

        AtomicReference<TicketCategory> threadResult = new AtomicReference<>();
        AtomicReference<Throwable> threadError = new AtomicReference<>();

        Thread inFlightThread = new Thread(() -> {
            try {
                threadResult.set(classifier.classify("Invoice issue", "Payment failed."));
            } catch (Throwable t) {
                threadError.set(t);
            }
        });
        inFlightThread.start();

        assertTrue(requestStarted.await(5, TimeUnit.SECONDS));
        assertEquals(0, classifier.getAvailablePermits());

        // A second concurrent request must fail fast immediately
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> classifier.classify("Second ticket", "Should fail fast.")
        );
        assertEquals("Ollama classification capacity reached", ex.getMessage());

        // Unblock first request
        releaseRequest.countDown();
        inFlightThread.join(5000);

        assertEquals(TicketCategory.PAYMENT, threadResult.get());
        assertEquals(1, classifier.getAvailablePermits());
    }

    @Test
    void requestTimeoutIsAppliedToHttpRequest() throws Exception {
        AtomicReference<HttpRequest> capturedRequest = new AtomicReference<>();

        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(chatResponse("{\"category\":\"BUG_REPORT\"}"));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    capturedRequest.set(invocation.getArgument(0));
                    return response;
                });

        OllamaTicketCategoryClassifier classifier = new OllamaTicketCategoryClassifier(
                "http://localhost:11434",
                "qwen3:4b",
                Duration.ofMillis(7500),
                new Semaphore(2),
                objectMapper,
                httpClient
        );

        classifier.classify("Title", "Description");

        assertNotNull(capturedRequest.get());
        assertTrue(capturedRequest.get().timeout().isPresent());
        assertEquals(Duration.ofMillis(7500), capturedRequest.get().timeout().get());
    }
}