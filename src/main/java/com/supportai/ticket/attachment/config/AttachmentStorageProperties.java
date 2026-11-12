package com.supportai.ticket.attachment.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized configuration properties for ticket attachment storage and quotas.
 * Supports overriding via APP_ATTACHMENTS_* environment variables under Spring Boot relaxed binding.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "app.attachments")
public class AttachmentStorageProperties {

    @NotBlank
    private String storageDir = "./uploads/attachments";

    @Positive
    private long maxFileSizeBytes = 10_485_760L;

    @Positive
    private int maxPerTicket = 20;

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public int getMaxPerTicket() {
        return maxPerTicket;
    }

    public void setMaxPerTicket(int maxPerTicket) {
        this.maxPerTicket = maxPerTicket;
    }
}

