package com.supportai.user.service;

import com.supportai.user.dto.UpdateUserRoleRequest;
import com.supportai.user.dto.UserSummaryResponse;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserRoleConcurrencyIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long adminAId;
    private Long adminBId;

    @BeforeEach
    void setUp() {
        cleanUpTestUsers();

        User adminA = new User(
                "Concurrent Admin A",
                "concurrent_admin_a@supportai.com",
                passwordEncoder.encode("SecurePass123!"),
                Role.ADMIN
        );
        User adminB = new User(
                "Concurrent Admin B",
                "concurrent_admin_b@supportai.com",
                passwordEncoder.encode("SecurePass123!"),
                Role.ADMIN
        );

        adminA = userRepository.save(adminA);
        adminB = userRepository.save(adminB);

        this.adminAId = adminA.getId();
        this.adminBId = adminB.getId();
    }

    @AfterEach
    void tearDown() {
        cleanUpTestUsers();
    }

    private void cleanUpTestUsers() {
        userRepository.findByEmail("concurrent_admin_a@supportai.com")
                .ifPresent(u -> userRepository.deleteById(u.getId()));
        userRepository.findByEmail("concurrent_admin_b@supportai.com")
                .ifPresent(u -> userRepository.deleteById(u.getId()));
    }

    @Test
    void concurrentDemotions_preventLeavingSystemWithoutAdministrator() throws Exception {
        // Assert initial state: exactly 2 test admins exist (plus any pre-existing)
        long initialAdminCount = userRepository.countByRole(Role.ADMIN);
        assertTrue(initialAdminCount >= 2, "Test requires at least two admins initially");

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<Throwable> expectedFailureException = new AtomicReference<>();

        // Task 1: Admin A attempts to demote Admin B to CUSTOMER
        Callable<UserSummaryResponse> task1 = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return userService.updateUserRole(
                    adminBId,
                    new UpdateUserRoleRequest(Role.CUSTOMER),
                    adminAId
            );
        };

        // Task 2: Admin B attempts to demote Admin A to CUSTOMER
        Callable<UserSummaryResponse> task2 = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return userService.updateUserRole(
                    adminAId,
                    new UpdateUserRoleRequest(Role.CUSTOMER),
                    adminBId
            );
        };

        Future<UserSummaryResponse> future1 = executor.submit(task1);
        Future<UserSummaryResponse> future2 = executor.submit(task2);

        // Collect outcomes for Task 1
        try {
            UserSummaryResponse res1 = future1.get(10, TimeUnit.SECONDS);
            if (res1 != null) {
                successCount.incrementAndGet();
            }
        } catch (ExecutionException e) {
            failureCount.incrementAndGet();
            expectedFailureException.set(e.getCause());
        }

        // Collect outcomes for Task 2
        try {
            UserSummaryResponse res2 = future2.get(10, TimeUnit.SECONDS);
            if (res2 != null) {
                successCount.incrementAndGet();
            }
        } catch (ExecutionException e) {
            failureCount.incrementAndGet();
            expectedFailureException.set(e.getCause());
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Crucial invariant: The platform must retain at least one administrator
        long remainingAdminCount = userRepository.countByRole(Role.ADMIN);
        assertTrue(
                remainingAdminCount >= 1,
                "The platform was left without an administrator! Current count: " + remainingAdminCount
        );

        if (initialAdminCount == 2) {
            // When only the two test administrators exist, exactly one demotion must succeed
            // and the competing demotion must be rejected by the last-administrator safeguard.
            assertEquals(1, successCount.get(), "Exactly one admin demotion must succeed when only two admins exist");
            assertEquals(1, failureCount.get(), "Competing admin demotion must fail when only two admins exist");

            // Verify the failure was specifically the last-admin rejection
            assertNotNull(expectedFailureException.get());
            assertTrue(
                    expectedFailureException.get() instanceof IllegalArgumentException,
                    "Expected IllegalArgumentException but got: " + expectedFailureException.get().getClass()
            );
            assertTrue(
                    expectedFailureException.get().getMessage().contains("Cannot demote the last remaining administrator"),
                    "Expected last remaining administrator message, but got: " + expectedFailureException.get().getMessage()
            );
            assertEquals(initialAdminCount - 1, remainingAdminCount, "Exactly one admin should have been demoted");
        } else {
            // When unrelated administrators exist in the database (initialAdminCount > 2),
            // both demotions legitimately succeed because the system still retains at least one administrator.
            assertEquals(2, successCount.get(), "Both admin demotions should succeed when unrelated admins exist");
            assertEquals(0, failureCount.get(), "No admin demotion should fail when unrelated admins exist");
            assertEquals(initialAdminCount - 2, remainingAdminCount, "Both test admins should have been demoted");
        }
    }
}
