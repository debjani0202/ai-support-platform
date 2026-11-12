package com.supportai.ticket.attachment.service;

import com.supportai.audit.entity.TicketAuditLog;
import com.supportai.audit.repository.TicketAuditLogRepository;
import com.supportai.audit.service.TicketAuditLogService;
import com.supportai.exception.ConflictException;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.ticket.attachment.dto.AttachmentResponse;
import com.supportai.ticket.attachment.entity.TicketAttachment;
import com.supportai.ticket.attachment.repository.TicketAttachmentRepository;
import com.supportai.ticket.entity.Ticket;
import com.supportai.ticket.entity.TicketCategory;
import com.supportai.ticket.entity.TicketPriority;
import com.supportai.ticket.repository.TicketRepository;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_DB_PASSWORD", matches = ".+")
class TicketAttachmentConcurrencyIntegrationTest {

    @TempDir
    static Path tempStorageRoot;

    @DynamicPropertySource
    static void registerAttachmentStorageDir(DynamicPropertyRegistry registry) {
        registry.add("app.attachments.storage-dir", () -> tempStorageRoot.toAbsolutePath().toString());
    }

    private static final String CUSTOMER_EMAIL = "attachment_concurrent_cust@supportai.com";
    private static final String AGENT_EMAIL = "attachment_concurrent_agent@supportai.com";

    @Autowired
    private TicketAttachmentService attachmentService;

    @Autowired
    private TicketAttachmentRepository attachmentRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketAuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private TicketAuditLogService auditLogService;

    private Long customerId;
    private Long ticketId;

    @BeforeEach
    void setUp() throws IOException {
        Mockito.reset(auditLogService);
        cleanUp();

        User customer = new User(
                "Attachment Customer",
                CUSTOMER_EMAIL,
                passwordEncoder.encode("Pass123!"),
                Role.CUSTOMER
        );
        User agent = new User(
                "Attachment Agent",
                AGENT_EMAIL,
                passwordEncoder.encode("Pass123!"),
                Role.AGENT
        );

        customer = userRepository.save(customer);
        agent = userRepository.save(agent);
        customerId = customer.getId();

        Ticket ticket = new Ticket("Concurrent Attachment Ticket", "Testing quota & transactions", "OPEN");
        ticket.setCategory(TicketCategory.BUG_REPORT);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setCreatedBy(customer);
        ticket.setAssignedTo(agent);
        ticket = ticketRepository.save(ticket);
        ticketId = ticket.getId();
    }

    @AfterEach
    void tearDown() throws IOException {
        Mockito.reset(auditLogService);
        cleanUp();
    }

    @Test
    void concurrentUploadsAtNineteen_allowsOnlyOneMore_andLeavesZeroOrphanedFiles() throws Exception {
        for (int i = 1; i <= 19; i++) {
            MockMultipartFile seed = new MockMultipartFile(
                    "file",
                    "seed-" + i + ".txt",
                    "text/plain",
                    ("seed content " + i).getBytes(StandardCharsets.UTF_8)
            );
            attachmentService.uploadAttachment(ticketId, seed, customerId, Role.CUSTOMER);
        }

        assertEquals(19L, attachmentRepository.countByTicketId(ticketId));
        assertEquals(19L, countPhysicalFiles());

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        MockMultipartFile upload = new MockMultipartFile(
                                "file",
                                "race-" + index + ".txt",
                                "text/plain",
                                ("concurrent payload " + index).getBytes(StandardCharsets.UTF_8)
                        );
                        attachmentService.uploadAttachment(ticketId, upload, customerId, Role.CUSTOMER);
                        successCount.incrementAndGet();
                    } catch (ConflictException ex) {
                        conflictCount.incrementAndGet();
                    } catch (Throwable ex) {
                        unexpectedErrors.add(ex);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS));
            startLatch.countDown();
            assertTrue(doneLatch.await(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertTrue(unexpectedErrors.isEmpty(), () -> "Unexpected errors: " + unexpectedErrors);
        assertEquals(1, successCount.get());
        assertEquals(threadCount - 1, conflictCount.get());
        assertEquals(20L, attachmentRepository.countByTicketId(ticketId));
        assertEquals(20L, countPhysicalFiles());

        List<TicketAuditLog> uploadAudits = auditLogRepository
                .findByTicketId(ticketId, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")))
                .stream()
                .filter(log -> "ATTACHMENT_UPLOADED".equals(log.getAction()))
                .toList();
        assertEquals(20, uploadAudits.size());
    }

    @Test
    void upload_whenAuditFails_rollsBackMetadataAndDeletesStoredFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "rollback-test.txt",
                "text/plain",
                "should be rolled back".getBytes(StandardCharsets.UTF_8)
        );

        doThrow(new RuntimeException("Simulated audit insert failure"))
                .when(auditLogService)
                .recordAction(any(Ticket.class), any(User.class), eq("ATTACHMENT_UPLOADED"), isNull(), anyString());

        assertThrows(RuntimeException.class, () ->
                attachmentService.uploadAttachment(ticketId, file, customerId, Role.CUSTOMER));

        assertEquals(0L, attachmentRepository.countByTicketId(ticketId));
        assertEquals(0L, countPhysicalFiles());
        assertTrue(auditLogRepository
                .findByTicketId(ticketId, Sort.by(Sort.Order.asc("id")))
                .stream()
                .noneMatch(log -> "ATTACHMENT_UPLOADED".equals(log.getAction())));
    }

    @Test
    void delete_preservesFileOnRollback_andDeletesFileAndWritesAuditOnlyAfterCommit() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lifecycle.txt",
                "text/plain",
                "lifecycle payload".getBytes(StandardCharsets.UTF_8)
        );
        AttachmentResponse uploaded = attachmentService.uploadAttachment(ticketId, file, customerId, Role.CUSTOMER);
        TicketAttachment entity = attachmentRepository.findByIdAndTicketId(uploaded.id(), ticketId).orElseThrow();
        Path storedPath = tempStorageRoot.resolve(entity.getStoredFilename());

        assertTrue(Files.exists(storedPath));

        // 1. Delete transaction marked rollback-only -> DB delete rolls back & file remains intact
        transactionTemplate.executeWithoutResult(status -> {
            attachmentService.deleteAttachment(ticketId, uploaded.id(), customerId, Role.CUSTOMER);
            status.setRollbackOnly();
        });

        assertEquals(1L, attachmentRepository.countByTicketId(ticketId));
        assertTrue(Files.exists(storedPath));

        // 2. Delete fails during audit -> DB delete rolls back & file remains intact
        doThrow(new RuntimeException("Simulated audit delete failure"))
                .when(auditLogService)
                .recordAction(any(Ticket.class), any(User.class), eq("ATTACHMENT_DELETED"), anyString(), isNull());

        assertThrows(RuntimeException.class, () ->
                attachmentService.deleteAttachment(ticketId, uploaded.id(), customerId, Role.CUSTOMER));
        assertEquals(1L, attachmentRepository.countByTicketId(ticketId));
        assertTrue(Files.exists(storedPath));

        Mockito.reset(auditLogService);

        // 3. Committed delete removes metadata, records audit, and physically deletes file after commit
        attachmentService.deleteAttachment(ticketId, uploaded.id(), customerId, Role.CUSTOMER);

        assertEquals(0L, attachmentRepository.countByTicketId(ticketId));
        assertFalse(Files.exists(storedPath));
        assertEquals(0L, countPhysicalFiles());

        List<TicketAuditLog> audits = auditLogRepository.findByTicketId(
                ticketId, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
        );
        assertEquals(1, audits.stream().filter(a -> "ATTACHMENT_UPLOADED".equals(a.getAction())).count());
        assertEquals(1, audits.stream().filter(a -> "ATTACHMENT_DELETED".equals(a.getAction())).count());

        // 4. Duplicate delete returns 404 without adding a second delete audit entry
        assertThrows(ResourceNotFoundException.class, () ->
                attachmentService.deleteAttachment(ticketId, uploaded.id(), customerId, Role.CUSTOMER));
        assertEquals(1, auditLogRepository
                .findByTicketId(ticketId, Sort.by(Sort.Order.asc("id")))
                .stream()
                .filter(a -> "ATTACHMENT_DELETED".equals(a.getAction()))
                .count());
    }

    private long countPhysicalFiles() throws IOException {
        if (!Files.exists(tempStorageRoot)) {
            return 0L;
        }
        try (Stream<Path> files = Files.list(tempStorageRoot)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private void cleanUp() throws IOException {
        jdbcTemplate.update("""
                DELETE FROM ticket_attachments
                WHERE ticket_id IN (
                    SELECT t.id FROM tickets t
                    JOIN users u ON t.created_by = u.id
                    WHERE u.email IN (?, ?)
                )
                """, CUSTOMER_EMAIL, AGENT_EMAIL);

        jdbcTemplate.update("""
                DELETE FROM ticket_audit_logs
                WHERE ticket_id IN (
                    SELECT t.id FROM tickets t
                    JOIN users u ON t.created_by = u.id
                    WHERE u.email IN (?, ?)
                )
                """, CUSTOMER_EMAIL, AGENT_EMAIL);

        jdbcTemplate.update("""
                DELETE FROM ticket_comments
                WHERE ticket_id IN (
                    SELECT t.id FROM tickets t
                    JOIN users u ON t.created_by = u.id
                    WHERE u.email IN (?, ?)
                )
                """, CUSTOMER_EMAIL, AGENT_EMAIL);

        jdbcTemplate.update("""
                DELETE FROM notifications
                WHERE recipient_id IN (
                    SELECT id FROM users WHERE email IN (?, ?)
                )
                """, CUSTOMER_EMAIL, AGENT_EMAIL);

        jdbcTemplate.update("""
                DELETE FROM tickets
                WHERE created_by IN (
                    SELECT id FROM users WHERE email IN (?, ?)
                )
                """, CUSTOMER_EMAIL, AGENT_EMAIL);

        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?)", CUSTOMER_EMAIL, AGENT_EMAIL);

        if (Files.exists(tempStorageRoot)) {
            try (Stream<Path> paths = Files.list(tempStorageRoot)) {
                for (Path p : paths.toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}

