package com.supportai.ticket.attachment.dto;

import org.springframework.core.io.Resource;

import java.util.Objects;

/**
 * Internal transfer object carrying a validated attachment Resource and safe download metadata.
 * Deliberately excludes server-side storage keys and local filesystem paths.
 */
public record AttachmentDownload(
        Resource resource,
        String originalFileName,
        String contentType,
        long fileSize
) {

    public AttachmentDownload {
        Objects.requireNonNull(resource, "Attachment resource must not be null");
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("Original filename must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content type must not be blank");
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("File size must be positive");
        }
    }

    /**
     * Omits Resource string representation so local filesystem paths are never logged.
     */
    @Override
    public String toString() {
        return "AttachmentDownload{" +
                "originalFileName='" + originalFileName + '\'' +
                ", contentType='" + contentType + '\'' +
                ", fileSize=" + fileSize +
                '}';
    }
}

