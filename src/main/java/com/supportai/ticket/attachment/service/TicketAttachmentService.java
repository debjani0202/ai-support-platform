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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

/**
 * Coordinates authorization, quota locking, validation, filesystem storage,
 * transactional metadata persistence, and audit logging for ticket attachments.
 */
@Service
@Transactional
public class TicketAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(TicketAttachmentService.class);
    private static final String CLOSED_STATUS = "CLOSED";
    private static final String AUDIT_ACTION_UPLOADED = "ATTACHMENT_UPLOADED";
    private static final String AUDIT_ACTION_DELETED = "ATTACHMENT_DELETED";

    private final TicketAttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AttachmentStorageProperties storageProperties;
    private final AttachmentFileValidator fileValidator;
    private final AttachmentStorageService storageService;
    private final TicketAuditLogService auditLogService;
    private final TransactionOperations transactionOperations;

    /**
     * Constructor for unit tests without a Spring TransactionOperations bean.
     */
    public TicketAttachmentService(
            TicketAttachmentRepository attachmentRepository,
            TicketRepository ticketRepository,
            UserRepository userRepository,
            AttachmentStorageProperties storageProperties,
            AttachmentFileValidator fileValidator,
            AttachmentStorageService storageService,
            TicketAuditLogService auditLogService) {
        this(
                attachmentRepository,
                ticketRepository,
                userRepository,
                storageProperties,
                fileValidator,
                storageService,
                auditLogService,
                TransactionOperations.withoutTransaction()
        );
    }

    /**
     * Primary constructor with transaction template injection.
     */
    @Autowired
    public TicketAttachmentService(
            TicketAttachmentRepository attachmentRepository,
            TicketRepository ticketRepository,
            UserRepository userRepository,
            AttachmentStorageProperties storageProperties,
            AttachmentFileValidator fileValidator,
            AttachmentStorageService storageService,
            TicketAuditLogService auditLogService,
            TransactionOperations transactionOperations) {
        this.attachmentRepository = attachmentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.storageProperties = storageProperties;
        this.fileValidator = fileValidator;
        this.storageService = storageService;
        this.auditLogService = auditLogService;
        this.transactionOperations = transactionOperations != null
                ? transactionOperations
                : TransactionOperations.withoutTransaction();
    }

    /**
     * Validates and uploads an attachment for an authorized active non-closed ticket,
     * enforcing per-ticket quotas under a pessimistic write lock on the parent ticket.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AttachmentResponse uploadAttachment(
            Long ticketId,
            MultipartFile file,
            Long currentUserId,
            Role currentUserRole) {

        // Step 1: Initial checks inside a brief non-locking transaction so lazy Ticket user refs stay safe.
        transactionOperations.executeWithoutResult(status -> {
            Ticket initialTicket = findActiveTicketById(ticketId);
            validateTicketAccess(initialTicket, currentUserId, currentUserRole);
            ensureTicketNotClosed(initialTicket, "Cannot upload attachments to a closed ticket");
        });

        // Step 2: Stream and validate file outside any database write lock.
        ValidatedAttachment validated = fileValidator.validate(file);

        // Steps 3-13: Acquire parent ticket row lock, verify quota, store file, persist metadata and audit log.
        return transactionOperations.execute(status -> {
            Ticket lockedTicket = attachmentRepository.findTicketByIdForUpdate(ticketId)
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with ID: " + ticketId));
            ensureActiveTicket(lockedTicket, ticketId);
            validateTicketAccess(lockedTicket, currentUserId, currentUserRole);
            ensureTicketNotClosed(lockedTicket, "Cannot upload attachments to a closed ticket");

            long existingCount = attachmentRepository.countByTicketId(ticketId);
            int maxPerTicket = storageProperties.getMaxPerTicket();
            if (existingCount >= maxPerTicket) {
                throw new ConflictException(
                        "Ticket has reached the maximum limit of " + maxPerTicket + " attachments"
                );
            }

            User uploader = findUserById(currentUserId);
            String storedFilename = storageService.store(file, validated.extension());

            boolean synchronizationRegistered = false;
            try {
                synchronizationRegistered = registerRollbackCleanup(storedFilename);

                TicketAttachment attachment = new TicketAttachment(
                        lockedTicket,
                        uploader,
                        validated.originalFilename(),
                        storedFilename,
                        validated.contentType(),
                        validated.fileSize()
                );

                TicketAttachment savedAttachment = attachmentRepository.saveAndFlush(attachment);

                auditLogService.recordAction(
                        lockedTicket,
                        uploader,
                        AUDIT_ACTION_UPLOADED,
                        null,
                        formatAuditSummary(savedAttachment)
                );

                return AttachmentResponse.from(savedAttachment);
            } catch (RuntimeException | Error ex) {
                if (!synchronizationRegistered) {
                    cleanupStoredFileQuietly(storedFilename);
                }
                throw ex;
            }
        });
    }

    /**
     * Lists a ticket's attachments in deterministic chronological order (createdAt ASC, id ASC).
     */
    @Transactional(readOnly = true)
    public PageResponse<AttachmentResponse> getAttachments(
            Long ticketId,
            Long currentUserId,
            Role currentUserRole,
            int page,
            int size) {

        if (page < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }

        Ticket ticket = findActiveTicketById(ticketId);
        validateTicketAccess(ticket, currentUserId, currentUserRole);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.asc("createdAt"),
                        Sort.Order.asc("id")
                )
        );

        Page<TicketAttachment> attachmentPage = attachmentRepository.findByTicketId(ticketId, pageable);

        return new PageResponse<>(
                attachmentPage.getContent()
                        .stream()
                        .map(AttachmentResponse::from)
                        .toList(),
                attachmentPage.getNumber(),
                attachmentPage.getSize(),
                attachmentPage.getTotalElements(),
                attachmentPage.getTotalPages(),
                attachmentPage.isFirst(),
                attachmentPage.isLast()
        );
    }

    /**
     * Loads a ticket-scoped attachment resource and validated metadata for forced download.
     */
    @Transactional(readOnly = true)
    public AttachmentDownload downloadAttachment(
            Long ticketId,
            Long attachmentId,
            Long currentUserId,
            Role currentUserRole) {

        Ticket ticket = findActiveTicketById(ticketId);
        validateTicketAccess(ticket, currentUserId, currentUserRole);

        TicketAttachment attachment = attachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with ID: " + attachmentId));

        Resource resource = storageService.loadAsResource(attachment.getStoredFilename());

        return new AttachmentDownload(
                resource,
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize()
        );
    }

    /**
     * Deletes an attachment metadata record and writes an audit event transactionally,
     * deleting the physical file only after the database commit succeeds.
     */
    public void deleteAttachment(
            Long ticketId,
            Long attachmentId,
            Long currentUserId,
            Role currentUserRole) {

        Ticket ticket = attachmentRepository.findTicketByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with ID: " + ticketId));
        ensureActiveTicket(ticket, ticketId);

        validateTicketAccess(ticket, currentUserId, currentUserRole);
        ensureTicketNotClosed(ticket, "Cannot delete attachments from a closed ticket");

        TicketAttachment attachment = attachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found with ID: " + attachmentId));

        validateDeletePermission(attachment, currentUserId, currentUserRole);

        User actor = findUserById(currentUserId);
        String storedFilename = attachment.getStoredFilename();
        String auditSummary = formatAuditSummary(attachment);

        attachmentRepository.delete(attachment);
        attachmentRepository.flush();

        auditLogService.recordAction(
                ticket,
                actor,
                AUDIT_ACTION_DELETED,
                auditSummary,
                null
        );

        schedulePhysicalDeletionAfterCommit(storedFilename);
    }

    private Ticket findActiveTicketById(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found with ID: " + ticketId));
        ensureActiveTicket(ticket, ticketId);
        return ticket;
    }

    private void ensureActiveTicket(Ticket ticket, Long ticketId) {
        if (ticket.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Ticket not found with ID: " + ticketId);
        }
    }

    private void ensureTicketNotClosed(Ticket ticket, String message) {
        if (CLOSED_STATUS.equalsIgnoreCase(ticket.getStatus())) {
            throw new ConflictException(message);
        }
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
    }

    private void validateTicketAccess(
            Ticket ticket,
            Long currentUserId,
            Role currentUserRole) {

        if (currentUserRole == Role.ADMIN) {
            return;
        }

        if (currentUserRole == Role.CUSTOMER) {
            if (ticket.getCreatedBy() == null
                    || !Objects.equals(ticket.getCreatedBy().getId(), currentUserId)) {
                throw new ForbiddenException("You are not allowed to access this ticket");
            }
            return;
        }

        if (currentUserRole == Role.AGENT) {
            if (ticket.getAssignedTo() == null
                    || !Objects.equals(ticket.getAssignedTo().getId(), currentUserId)) {
                throw new ForbiddenException("You are not allowed to access this ticket");
            }
            return;
        }

        throw new ForbiddenException("You are not allowed to access this ticket");
    }

    private void validateDeletePermission(
            TicketAttachment attachment,
            Long currentUserId,
            Role currentUserRole) {

        if (currentUserRole == Role.ADMIN) {
            return;
        }

        if (currentUserRole == Role.CUSTOMER || currentUserRole == Role.AGENT) {
            if (attachment.getUploadedBy() == null
                    || !Objects.equals(attachment.getUploadedBy().getId(), currentUserId)) {
                throw new ForbiddenException("You are only allowed to delete your own attachments");
            }
            return;
        }

        throw new ForbiddenException("You are not allowed to delete this attachment");
    }

    private boolean registerRollbackCleanup(String storedFilename) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    cleanupStoredFileQuietly(storedFilename);
                }
            }
        });
        return true;
    }

    private void schedulePhysicalDeletionAfterCommit(String storedFilename) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deletePhysicalFileQuietly(storedFilename);
                }
            });
            return;
        }

        deletePhysicalFileQuietly(storedFilename);
    }

    private void cleanupStoredFileQuietly(String storedFilename) {
        try {
            storageService.deleteIfExists(storedFilename);
        } catch (RuntimeException cleanupEx) {
            log.warn("Failed to clean up rolled-back attachment file for key {}", storedFilename, cleanupEx);
        }
    }

    private void deletePhysicalFileQuietly(String storedFilename) {
        try {
            storageService.deleteIfExists(storedFilename);
        } catch (RuntimeException cleanupEx) {
            log.error("Failed to delete committed attachment file for key {}", storedFilename, cleanupEx);
        }
    }

    private String formatAuditSummary(TicketAttachment attachment) {
        return "Attachment #" + attachment.getId()
                + ": " + attachment.getOriginalFilename()
                + " (" + attachment.getFileSize() + " bytes)";
    }
}

