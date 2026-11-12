package com.supportai.ticket.attachment.service;

import com.supportai.audit.service.TicketAuditLogService;
import com.supportai.exception.ConflictException;
import com.supportai.exception.ForbiddenException;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.ticket.attachment.config.AttachmentStorageProperties;
import com.supportai.ticket.attachment.dto.AttachmentDownload;
import com.supportai.ticket.attachment.dto.AttachmentResponse;
import com.supportai.ticket.attachment.entity.TicketAttachment;
import com.supportai.ticket.attachment.repository.TicketAttachmentRepository;
import com.supportai.ticket.attachment.validation.AttachmentFileValidator;
import com.supportai.ticket.attachment.validation.AttachmentFileValidator.ValidatedAttachment;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.ticket.entity.Ticket;
import com.supportai.ticket.repository.TicketRepository;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketAttachmentServiceTest {

    @Mock
    private TicketAttachmentRepository attachmentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AttachmentFileValidator fileValidator;

    @Mock
    private AttachmentStorageService storageService;

    @Mock
    private TicketAuditLogService auditLogService;

    private AttachmentStorageProperties storageProperties;
    private TicketAttachmentService attachmentService;

    private Long ticketId;
    private Long customerId;
    private Long agentId;
    private Long adminId;
    private User customer;
    private User agent;
    private User admin;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        storageProperties = new AttachmentStorageProperties();
        storageProperties.setMaxPerTicket(20);
        storageProperties.setMaxFileSizeBytes(10_485_760L);

        attachmentService = new TicketAttachmentService(
                attachmentRepository,
                ticketRepository,
                userRepository,
                storageProperties,
                fileValidator,
                storageService,
                auditLogService
        );

        ticketId = 100L;
        customerId = 10L;
        agentId = 20L;
        adminId = 1L;

        customer = mock(User.class);
        lenient().when(customer.getId()).thenReturn(customerId);
        lenient().when(customer.getName()).thenReturn("Alice Customer");

        agent = mock(User.class);
        lenient().when(agent.getId()).thenReturn(agentId);
        lenient().when(agent.getName()).thenReturn("Bob Agent");

        admin = mock(User.class);
        lenient().when(admin.getId()).thenReturn(adminId);
        lenient().when(admin.getName()).thenReturn("Carol Admin");

        ticket = new Ticket("Cannot export report", "Fails on PDF export", "OPEN");
        ReflectionTestUtils.setField(ticket, "id", ticketId);
        ticket.setCreatedBy(customer);
        ticket.setAssignedTo(agent);
    }

    @Test
    void uploadAttachment_succeedsForCreatorCustomerAssignedAgentAndAdmin() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "%PDF-1.7\nbody".getBytes(StandardCharsets.US_ASCII)
        );
        ValidatedAttachment validated = new ValidatedAttachment("report.pdf", "pdf", "application/pdf", 12L);

        stubTicketReadAndLock(ticket);
        when(fileValidator.validate(file)).thenReturn(validated);
        when(attachmentRepository.countByTicketId(ticketId)).thenReturn(0L, 1L, 2L);
        when(storageService.store(file, "pdf"))
                .thenReturn("11111111-1111-1111-1111-111111111111.pdf")
                .thenReturn("22222222-2222-2222-2222-222222222222.pdf")
                .thenReturn("33333333-3333-3333-3333-333333333333.pdf");

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        when(attachmentRepository.saveAndFlush(any(TicketAttachment.class))).thenAnswer(invocation -> {
            TicketAttachment entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 501L);
            ReflectionTestUtils.setField(entity, "createdAt", LocalDateTime.of(2026, 11, 11, 12, 0));
            return entity;
        });

        AttachmentResponse customerRes = attachmentService.uploadAttachment(ticketId, file, customerId, Role.CUSTOMER);
        assertEquals(501L, customerRes.id());
        assertEquals(ticketId, customerRes.ticketId());
        assertEquals(customerId, customerRes.uploadedById());
        assertEquals("Alice Customer", customerRes.uploadedByName());
        assertEquals("report.pdf", customerRes.originalFileName());
        assertEquals("application/pdf", customerRes.contentType());
        assertEquals(12L, customerRes.fileSize());

        AttachmentResponse agentRes = attachmentService.uploadAttachment(ticketId, file, agentId, Role.AGENT);
        assertEquals(agentId, agentRes.uploadedById());

        AttachmentResponse adminRes = attachmentService.uploadAttachment(ticketId, file, adminId, Role.ADMIN);
        assertEquals(adminId, adminRes.uploadedById());

        ArgumentCaptor<String> auditNewValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, org.mockito.Mockito.times(3)).recordAction(
                eq(ticket),
                any(User.class),
                eq("ATTACHMENT_UPLOADED"),
                isNull(),
                auditNewValueCaptor.capture()
        );
        for (String auditValue : auditNewValueCaptor.getAllValues()) {
            assertTrue(auditValue.contains("report.pdf"));
            assertTrue(auditValue.contains("12 bytes"));
        }
    }

    @Test
    void uploadListDownloadDelete_unauthorizedCustomerAgentAndUnknownRole_throwForbidden() {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.findTicketByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));

        assertThrows(ForbiddenException.class, () ->
                attachmentService.uploadAttachment(ticketId, file, 999L, Role.CUSTOMER));
        assertThrows(ForbiddenException.class, () ->
                attachmentService.uploadAttachment(ticketId, file, 888L, Role.AGENT));
        assertThrows(ForbiddenException.class, () ->
                attachmentService.uploadAttachment(ticketId, file, customerId, null));

        assertThrows(ForbiddenException.class, () ->
                attachmentService.getAttachments(ticketId, 999L, Role.CUSTOMER, 0, 20));
        assertThrows(ForbiddenException.class, () ->
                attachmentService.getAttachments(ticketId, 888L, Role.AGENT, 0, 20));
        assertThrows(ForbiddenException.class, () ->
                attachmentService.getAttachments(ticketId, customerId, null, 0, 20));

        assertThrows(ForbiddenException.class, () ->
                attachmentService.downloadAttachment(ticketId, 1L, 999L, Role.CUSTOMER));
        assertThrows(ForbiddenException.class, () ->
                attachmentService.downloadAttachment(ticketId, 1L, 888L, Role.AGENT));
        assertThrows(ForbiddenException.class, () ->
                attachmentService.downloadAttachment(ticketId, 1L, customerId, null));

        assertThrows(ForbiddenException.class, () ->
                attachmentService.deleteAttachment(ticketId, 1L, 999L, Role.CUSTOMER));
        assertThrows(ForbiddenException.class, () ->
                attachmentService.deleteAttachment(ticketId, 1L, 888L, Role.AGENT));
        assertThrows(ForbiddenException.class, () ->
                attachmentService.deleteAttachment(ticketId, 1L, customerId, null));

        verifyNoInteractions(fileValidator, storageService, auditLogService);
    }

    @Test
    void softDeletedOrMissingTicket_returns404AcrossAllOperationsEvenForAdmin() {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        ticket.setDeletedAt(LocalDateTime.now().minusMinutes(5));
        stubTicketReadAndLock(ticket);

        for (Role role : Role.values()) {
            Long userId = switch (role) {
                case ADMIN -> adminId;
                case CUSTOMER -> customerId;
                case AGENT -> agentId;
            };
            assertThrows(ResourceNotFoundException.class, () ->
                    attachmentService.uploadAttachment(ticketId, file, userId, role));
            assertThrows(ResourceNotFoundException.class, () ->
                    attachmentService.getAttachments(ticketId, userId, role, 0, 20));
            assertThrows(ResourceNotFoundException.class, () ->
                    attachmentService.downloadAttachment(ticketId, 1L, userId, role));
            assertThrows(ResourceNotFoundException.class, () ->
                    attachmentService.deleteAttachment(ticketId, 1L, userId, role));
        }

        when(ticketRepository.findById(404L)).thenReturn(Optional.empty());
        when(attachmentRepository.findTicketByIdForUpdate(404L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () ->
                attachmentService.uploadAttachment(404L, file, adminId, Role.ADMIN));
        assertThrows(ResourceNotFoundException.class, () ->
                attachmentService.getAttachments(404L, adminId, Role.ADMIN, 0, 20));
        assertThrows(ResourceNotFoundException.class, () ->
                attachmentService.downloadAttachment(404L, 1L, adminId, Role.ADMIN));
        assertThrows(ResourceNotFoundException.class, () ->
                attachmentService.deleteAttachment(404L, 1L, adminId, Role.ADMIN));

        verifyNoInteractions(fileValidator, storageService, auditLogService);
    }

    @Test
    void closedTicket_blocksUploadAndDeleteWith409_whileAllowingListAndDownload() {
        ticket.setStatus("CLOSED");
        stubTicketReadAndLock(ticket);

        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        assertThrows(ConflictException.class, () ->
                attachmentService.uploadAttachment(ticketId, file, customerId, Role.CUSTOMER));
        assertThrows(ConflictException.class, () ->
                attachmentService.uploadAttachment(ticketId, file, adminId, Role.ADMIN));

        TicketAttachment storedAttachment = createAttachmentEntity(
                9L, ticket, customer, "closed-doc.txt", "11111111-1111-1111-1111-111111111111.txt", "text/plain", 9L
        );
        when(attachmentRepository.findByIdAndTicketId(9L, ticketId)).thenReturn(Optional.of(storedAttachment));

        assertThrows(ConflictException.class, () ->
                attachmentService.deleteAttachment(ticketId, 9L, customerId, Role.CUSTOMER));
        assertThrows(ConflictException.class, () ->
                attachmentService.deleteAttachment(ticketId, 9L, adminId, Role.ADMIN));

        Page<TicketAttachment> page = new PageImpl<>(List.of(storedAttachment), PageRequest.of(0, 20), 1);
        when(attachmentRepository.findByTicketId(eq(ticketId), any(Pageable.class))).thenReturn(page);
        PageResponse<AttachmentResponse> listResult = attachmentService.getAttachments(ticketId, customerId, Role.CUSTOMER, 0, 20);
        assertEquals(1, listResult.content().size());

        Resource resource = new ByteArrayResource("closed ok".getBytes(StandardCharsets.UTF_8));
        when(storageService.loadAsResource(storedAttachment.getStoredFilename())).thenReturn(resource);
        AttachmentDownload download = attachmentService.downloadAttachment(ticketId, 9L, agentId, Role.AGENT);
        assertSame(resource, download.resource());
        assertEquals("closed-doc.txt", download.originalFileName());

        verify(storageService, never()).store(any(), anyString());
        verify(storageService, never()).deleteIfExists(anyString());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void uploadAttachment_quotaReached_throws409ConflictBeforeStoringFile() {
        MockMultipartFile file = new MockMultipartFile("file", "extra.txt", "text/plain", "overflow".getBytes(StandardCharsets.UTF_8));
        ValidatedAttachment validated = new ValidatedAttachment("extra.txt", "txt", "text/plain", 8L);

        stubTicketReadAndLock(ticket);
        when(fileValidator.validate(file)).thenReturn(validated);
        when(attachmentRepository.countByTicketId(ticketId)).thenReturn(20L);

        assertThrows(ConflictException.class, () ->
                attachmentService.uploadAttachment(ticketId, file, customerId, Role.CUSTOMER));

        verify(attachmentRepository).findTicketByIdForUpdate(ticketId);
        verify(storageService, never()).store(any(), anyString());
        verify(attachmentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void uploadAttachment_metadataOrAuditFailure_cleansUpNewlyStoredFile() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "data".getBytes(StandardCharsets.UTF_8));
        ValidatedAttachment validated = new ValidatedAttachment("notes.txt", "txt", "text/plain", 4L);
        String key = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.txt";

        stubTicketReadAndLock(ticket);
        when(fileValidator.validate(file)).thenReturn(validated);
        when(attachmentRepository.countByTicketId(ticketId)).thenReturn(0L);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(storageService.store(file, "txt")).thenReturn(key);
        when(attachmentRepository.saveAndFlush(any(TicketAttachment.class)))
                .thenThrow(new RuntimeException("DB write failed"));
        doThrow(new IllegalStateException("Cleanup also failed"))
                .when(storageService).deleteIfExists(key);

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                attachmentService.uploadAttachment(ticketId, file, customerId, Role.CUSTOMER));
        assertEquals("DB write failed", thrown.getMessage());
        verify(storageService).deleteIfExists(key);
    }

    @Test
    void uploadAttachment_auditFailureAfterSave_cleansUpNewlyStoredFile() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "data".getBytes(StandardCharsets.UTF_8));
        ValidatedAttachment validated = new ValidatedAttachment("notes.txt", "txt", "text/plain", 4L);
        String key = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.txt";

        stubTicketReadAndLock(ticket);
        when(fileValidator.validate(file)).thenReturn(validated);
        when(attachmentRepository.countByTicketId(ticketId)).thenReturn(0L);
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(storageService.store(file, "txt")).thenReturn(key);
        when(attachmentRepository.saveAndFlush(any(TicketAttachment.class))).thenAnswer(invocation -> {
            TicketAttachment entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 77L);
            return entity;
        });
        doThrow(new RuntimeException("Audit failure"))
                .when(auditLogService).recordAction(eq(ticket), eq(customer), eq("ATTACHMENT_UPLOADED"), isNull(), anyString());

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                attachmentService.uploadAttachment(ticketId, file, customerId, Role.CUSTOMER));
        assertEquals("Audit failure", thrown.getMessage());
        verify(storageService).deleteIfExists(key);
    }

    @Test
    void getAttachments_validatesPaginationAndUsesChronologicalSort() {
        assertThrows(IllegalArgumentException.class, () ->
                attachmentService.getAttachments(ticketId, customerId, Role.CUSTOMER, -1, 20));
        assertThrows(IllegalArgumentException.class, () ->
                attachmentService.getAttachments(ticketId, customerId, Role.CUSTOMER, 0, 0));
        assertThrows(IllegalArgumentException.class, () ->
                attachmentService.getAttachments(ticketId, customerId, Role.CUSTOMER, 0, 101));

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        TicketAttachment item = createAttachmentEntity(
                15L, ticket, customer, "capture.png", "cccccccc-cccc-cccc-cccc-cccccccccccc.png", "image/png", 256L
        );
        PageRequest expectedPageable = PageRequest.of(
                1,
                10,
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
        );
        Page<TicketAttachment> page = new PageImpl<>(List.of(item), expectedPageable, 11);
        when(attachmentRepository.findByTicketId(eq(ticketId), any(Pageable.class))).thenReturn(page);

        PageResponse<AttachmentResponse> response =
                attachmentService.getAttachments(ticketId, customerId, Role.CUSTOMER, 1, 10);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(attachmentRepository).findByTicketId(eq(ticketId), pageableCaptor.capture());
        assertEquals(expectedPageable, pageableCaptor.getValue());

        assertEquals(1, response.content().size());
        assertEquals(15L, response.content().get(0).id());
        assertEquals(1, response.page());
        assertEquals(10, response.size());
        assertEquals(11L, response.totalElements());
        assertEquals(2, response.totalPages());
        assertEquals(false, response.first());
        assertEquals(true, response.last());
    }

    @Test
    void downloadAttachment_enforcesTicketScopingAndMissingDiskFile404() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.findByIdAndTicketId(88L, ticketId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                attachmentService.downloadAttachment(ticketId, 88L, customerId, Role.CUSTOMER));

        TicketAttachment attachment = createAttachmentEntity(
                12L, ticket, customer, "logs.txt", "dddddddd-dddd-dddd-dddd-dddddddddddd.txt", "text/plain", 42L
        );
        when(attachmentRepository.findByIdAndTicketId(12L, ticketId)).thenReturn(Optional.of(attachment));
        when(storageService.loadAsResource(attachment.getStoredFilename()))
                .thenThrow(new ResourceNotFoundException("Attachment file not found"));

        assertThrows(ResourceNotFoundException.class, () ->
                attachmentService.downloadAttachment(ticketId, 12L, customerId, Role.CUSTOMER));
    }

    @Test
    void deleteAttachment_enforcesUploaderOwnershipAndAdminOverride() {
        TicketAttachment customerAttachment = createAttachmentEntity(
                21L, ticket, customer, "customer-file.txt", "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee.txt", "text/plain", 15L
        );
        TicketAttachment agentAttachment = createAttachmentEntity(
                22L, ticket, agent, "agent-file.txt", "ffffffff-ffff-ffff-ffff-ffffffffffff.txt", "text/plain", 25L
        );

        when(attachmentRepository.findTicketByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.findByIdAndTicketId(21L, ticketId)).thenReturn(Optional.of(customerAttachment));
        when(attachmentRepository.findByIdAndTicketId(22L, ticketId)).thenReturn(Optional.of(agentAttachment));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(userRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // Agent cannot delete customer's upload; customer cannot delete agent's upload
        assertThrows(ForbiddenException.class, () ->
                attachmentService.deleteAttachment(ticketId, 21L, agentId, Role.AGENT));
        assertThrows(ForbiddenException.class, () ->
                attachmentService.deleteAttachment(ticketId, 22L, customerId, Role.CUSTOMER));

        verify(attachmentRepository, never()).delete(any());
        verify(storageService, never()).deleteIfExists(anyString());
        verifyNoInteractions(auditLogService);

        // Customer can delete own upload
        attachmentService.deleteAttachment(ticketId, 21L, customerId, Role.CUSTOMER);
        // Agent can delete own upload
        attachmentService.deleteAttachment(ticketId, 22L, agentId, Role.AGENT);
        // Admin can delete any upload
        attachmentService.deleteAttachment(ticketId, 21L, adminId, Role.ADMIN);

        verify(storageService, org.mockito.Mockito.times(2)).deleteIfExists(customerAttachment.getStoredFilename());
        verify(storageService).deleteIfExists(agentAttachment.getStoredFilename());
        verify(auditLogService, org.mockito.Mockito.times(3))
                .recordAction(eq(ticket), any(User.class), eq("ATTACHMENT_DELETED"), anyString(), isNull());
    }

    @Test
    void deleteAttachment_dbOrAuditFailure_neverDeletesPhysicalFile() {
        TicketAttachment customerAttachment = createAttachmentEntity(
                31L, ticket, customer, "safe.pdf", "12121212-1212-1212-1212-121212121212.pdf", "application/pdf", 100L
        );

        when(attachmentRepository.findTicketByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.findByIdAndTicketId(31L, ticketId)).thenReturn(Optional.of(customerAttachment));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        doThrow(new RuntimeException("Audit DB error"))
                .when(auditLogService).recordAction(eq(ticket), eq(customer), eq("ATTACHMENT_DELETED"), anyString(), isNull());

        assertThrows(RuntimeException.class, () ->
                attachmentService.deleteAttachment(ticketId, 31L, customerId, Role.CUSTOMER));

        InOrder order = inOrder(attachmentRepository, auditLogService);
        order.verify(attachmentRepository).delete(customerAttachment);
        order.verify(attachmentRepository).flush();
        order.verify(auditLogService).recordAction(eq(ticket), eq(customer), eq("ATTACHMENT_DELETED"), anyString(), isNull());

        verify(storageService, never()).deleteIfExists(anyString());
    }

    @Test
    void deleteAttachment_missingOrWrongTicketAttachment_returns404WithoutAuditOrDiskDelete() {
        when(attachmentRepository.findTicketByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.findByIdAndTicketId(999L, ticketId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                attachmentService.deleteAttachment(ticketId, 999L, adminId, Role.ADMIN));

        verify(attachmentRepository, never()).delete(any());
        verify(storageService, never()).deleteIfExists(anyString());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void deleteAttachment_closedTicketWithMissingAttachment_throws409BeforeAttachmentLookup() {
        ticket.setStatus("CLOSED");
        when(attachmentRepository.findTicketByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () ->
                attachmentService.deleteAttachment(ticketId, 999L, customerId, Role.CUSTOMER));
        assertThrows(ConflictException.class, () ->
                attachmentService.deleteAttachment(ticketId, 999L, agentId, Role.AGENT));
        assertThrows(ConflictException.class, () ->
                attachmentService.deleteAttachment(ticketId, 999L, adminId, Role.ADMIN));
        assertThrows(ForbiddenException.class, () ->
                attachmentService.deleteAttachment(ticketId, 999L, 999L, Role.CUSTOMER));

        verify(attachmentRepository, never()).findByIdAndTicketId(anyLong(), anyLong());
        verify(attachmentRepository, never()).delete(any());
        verifyNoInteractions(storageService, auditLogService);
    }

    private void stubTicketReadAndLock(Ticket ticketEntity) {
        lenient().when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticketEntity));
        lenient().when(attachmentRepository.findTicketByIdForUpdate(ticketId)).thenReturn(Optional.of(ticketEntity));
    }

    private TicketAttachment createAttachmentEntity(
            Long id,
            Ticket parentTicket,
            User uploader,
            String originalFilename,
            String storedFilename,
            String contentType,
            long fileSize) {
        TicketAttachment attachment = new TicketAttachment(
                parentTicket,
                uploader,
                originalFilename,
                storedFilename,
                contentType,
                fileSize
        );
        ReflectionTestUtils.setField(attachment, "id", id);
        ReflectionTestUtils.setField(attachment, "createdAt", LocalDateTime.of(2026, 11, 11, 10, 0));
        return attachment;
    }
}

