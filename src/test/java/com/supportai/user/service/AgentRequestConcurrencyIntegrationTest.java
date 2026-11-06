
package com.supportai.user.service;

import com.supportai.user.dto.ApproveAgentRequestDto;
import com.supportai.user.dto.CreateAgentRequestDto;
import com.supportai.user.dto.RejectAgentRequestDto;
import com.supportai.user.entity.AgentRequest;
import com.supportai.user.entity.AgentRequestStatus;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.AgentRequestRepository;
import com.supportai.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
        named = "TEST_DB_PASSWORD", matches = ".+"
)
class AgentRequestConcurrencyIntegrationTest {

    @Autowired
    private AgentRequestService agentRequestService;

    @Autowired
    private AgentRequestRepository agentRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long customerId;
    private Long adminAId;
    private Long adminBId;

    private static final String CUSTOMER_EMAIL =
            "concurrent_cust@supportai.com";
    private static final String ADMIN_A_EMAIL =
            "admin_alpha@supportai.com";
    private static final String ADMIN_B_EMAIL =
            "admin_beta@supportai.com";

    @BeforeEach
    void setUp() {
        cleanUp();

        User customer = new User(
                "Concurrent Customer",
                CUSTOMER_EMAIL,
                passwordEncoder.encode("Pass123!"),
                Role.CUSTOMER
        );

        User adminA = new User(
                "Admin Alpha",
                ADMIN_A_EMAIL,
                passwordEncoder.encode("Pass123!"),
                Role.ADMIN
        );

        User adminB = new User(
                "Admin Beta",
                ADMIN_B_EMAIL,
                passwordEncoder.encode("Pass123!"),
                Role.ADMIN
        );

        customerId = userRepository.save(customer).getId();
        adminAId = userRepository.save(adminA).getId();
        adminBId = userRepository.save(adminB).getId();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        // Delete only requests belonging to this test's users.
        jdbcTemplate.update("""
                DELETE FROM agent_requests
                WHERE user_id IN (
                    SELECT id FROM users
                    WHERE email IN (?, ?, ?)
                )
                """,
                CUSTOMER_EMAIL,
                ADMIN_A_EMAIL,
                ADMIN_B_EMAIL
        );

        userRepository.findByEmail(CUSTOMER_EMAIL)
                .ifPresent(userRepository::delete);

        userRepository.findByEmail(ADMIN_A_EMAIL)
                .ifPresent(userRepository::delete);

        userRepository.findByEmail(ADMIN_B_EMAIL)
                .ifPresent(userRepository::delete);
    }

    @Test
    void concurrentSubmissions_onlyOnePendingSucceeds()
            throws InterruptedException {

        int threadCount = 2;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateErrorCount = new AtomicInteger();

        ConcurrentLinkedQueue<Throwable> unexpectedErrors =
                new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        agentRequestService.submitRequest(
                                customerId,
                                new CreateAgentRequestDto("Concurrent test")
                        );

                        successCount.incrementAndGet();

                    } catch (IllegalArgumentException ex) {
                        if (ex.getMessage() != null &&
                                ex.getMessage().contains(
                                        "already have an agent access request pending review"
                                )) {
                            duplicateErrorCount.incrementAndGet();
                        } else {
                            unexpectedErrors.add(ex);
                        }

                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        unexpectedErrors.add(ex);

                    } catch (Exception ex) {
                        unexpectedErrors.add(ex);

                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            startLatch.countDown();

            assertTrue(
                    finishLatch.await(10, TimeUnit.SECONDS),
                    "Threads must finish in 10 seconds"
            );

            assertTrue(
                    unexpectedErrors.isEmpty(),
                    "Unexpected thread errors: " + unexpectedErrors
            );

            assertEquals(
                    1,
                    successCount.get(),
                    "Exactly one concurrent submission must succeed"
            );

            assertEquals(
                    1,
                    duplicateErrorCount.get(),
                    "The duplicate submission must be rejected"
            );

        } finally {
            executor.shutdownNow();
            assertTrue(
                    executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Executor must terminate"
            );
        }
    }

    @Test
    void concurrentReviews_onlyOneTransitionSucceeds()
            throws InterruptedException {

        agentRequestService.submitRequest(
                customerId,
                new CreateAgentRequestDto("Initial application")
        );

        AgentRequest request = agentRequestRepository
                .findTopByUserIdOrderByCreatedAtDesc(customerId)
                .orElseThrow();

        Long requestId = request.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);

        AtomicInteger transitionSuccess = new AtomicInteger();
        AtomicInteger transitionRejected = new AtomicInteger();

        ConcurrentLinkedQueue<Throwable> unexpectedErrors =
                new ConcurrentLinkedQueue<>();

        try {
            // Admin A attempts approval.
            executor.submit(() -> {
                try {
                    startLatch.await();

                    agentRequestService.approveRequest(
                            requestId,
                            new ApproveAgentRequestDto("Welcome"),
                            adminAId
                    );

                    transitionSuccess.incrementAndGet();

                } catch (IllegalArgumentException ex) {
                    transitionRejected.incrementAndGet();

                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(ex);

                } catch (Exception ex) {
                    unexpectedErrors.add(ex);

                } finally {
                    finishLatch.countDown();
                }
            });

            // Admin B attempts rejection.
            executor.submit(() -> {
                try {
                    startLatch.await();

                    agentRequestService.rejectRequest(
                            requestId,
                            new RejectAgentRequestDto("Not qualified"),
                            adminBId
                    );

                    transitionSuccess.incrementAndGet();

                } catch (IllegalArgumentException ex) {
                    transitionRejected.incrementAndGet();

                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    unexpectedErrors.add(ex);

                } catch (Exception ex) {
                    unexpectedErrors.add(ex);

                } finally {
                    finishLatch.countDown();
                }
            });

            startLatch.countDown();

            assertTrue(
                    finishLatch.await(10, TimeUnit.SECONDS),
                    "Threads must finish in 10 seconds"
            );

            assertTrue(
                    unexpectedErrors.isEmpty(),
                    "Unexpected thread errors: " + unexpectedErrors
            );

            assertEquals(
                    1,
                    transitionSuccess.get(),
                    "Exactly one admin transition must succeed"
            );

            assertEquals(
                    1,
                    transitionRejected.get(),
                    "The second concurrent transition must be rejected"
            );

            AgentRequest finalRequest = agentRequestRepository
                    .findById(requestId)
                    .orElseThrow();

            assertNotEquals(
                    AgentRequestStatus.PENDING,
                    finalRequest.getStatus()
            );

        } finally {
            executor.shutdownNow();
            assertTrue(
                    executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Executor must terminate"
            );
        }
    }
}
