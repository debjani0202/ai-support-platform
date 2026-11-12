package com.supportai.ticket.attachment.controller;

import com.supportai.exception.ForbiddenException;
import com.supportai.ticket.attachment.dto.AttachmentDownload;
import com.supportai.ticket.attachment.dto.AttachmentResponse;
import com.supportai.ticket.attachment.service.TicketAttachmentService;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.user.entity.Role;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * Exposes REST endpoints for uploading, listing, downloading, and deleting ticket attachments.
 */
@RestController
@RequestMapping("/api/tickets/{ticketId}/attachments")
public class TicketAttachmentController {

    private final TicketAttachmentService attachmentService;

    public TicketAttachmentController(TicketAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * Uploads an attachment to an active non-closed ticket.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse uploadAttachment(
            @PathVariable Long ticketId,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {

        Long currentUserId = Long.valueOf(authentication.getName());
        Role currentUserRole = getCurrentUserRole(authentication);

        return attachmentService.uploadAttachment(
                ticketId,
                file,
                currentUserId,
                currentUserRole
        );
    }

    /**
     * Lists attachments for a ticket with pagination in ascending chronological order.
     */
    @GetMapping
    public PageResponse<AttachmentResponse> getAttachments(
            @PathVariable Long ticketId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        Long currentUserId = Long.valueOf(authentication.getName());
        Role currentUserRole = getCurrentUserRole(authentication);

        return attachmentService.getAttachments(
                ticketId,
                currentUserId,
                currentUserRole,
                page,
                size
        );
    }

    /**
     * Streams a validated attachment download with hardened response headers.
     */
    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId,
            Authentication authentication) {

        Long currentUserId = Long.valueOf(authentication.getName());
        Role currentUserRole = getCurrentUserRole(authentication);

        AttachmentDownload download = attachmentService.downloadAttachment(
                ticketId,
                attachmentId,
                currentUserId,
                currentUserRole
        );

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(download.originalFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(download.resource());
    }

    /**
     * Deletes an attachment according to uploader ownership and ticket state rules.
     */
    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId,
            Authentication authentication) {

        Long currentUserId = Long.valueOf(authentication.getName());
        Role currentUserRole = getCurrentUserRole(authentication);

        attachmentService.deleteAttachment(
                ticketId,
                attachmentId,
                currentUserId,
                currentUserRole
        );
    }

    private Role getCurrentUserRole(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority != null && authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .map(roleName -> {
                    try {
                        return Role.valueOf(roleName);
                    } catch (IllegalArgumentException ex) {
                        throw new ForbiddenException("Invalid user role");
                    }
                })
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("User role not found"));
    }
}

