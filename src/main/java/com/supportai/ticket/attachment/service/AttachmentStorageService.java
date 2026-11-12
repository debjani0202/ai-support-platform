package com.supportai.ticket.attachment.service;

import com.supportai.exception.ResourceNotFoundException;
import com.supportai.ticket.attachment.config.AttachmentStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Local filesystem storage service for ticket attachments.
 * Operates exclusively via opaque UUID storage keys confined inside the configured storage root.
 */
@Service
public class AttachmentStorageService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStorageService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "txt");
    private static final Pattern STORAGE_KEY_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(pdf|png|jpg|jpeg|txt)$"
    );

    private final Path storageRoot;

    @Autowired
    public AttachmentStorageService(AttachmentStorageProperties storageProperties) {
        if (storageProperties == null
                || storageProperties.getStorageDir() == null
                || storageProperties.getStorageDir().isBlank()) {
            throw new IllegalStateException("Attachment storage directory must be configured");
        }
        this.storageRoot = Path.of(storageProperties.getStorageDir()).toAbsolutePath().normalize();
    }

    /**
     * Streams a validated uploaded file into the configured storage directory under a generated UUID filename.
     *
     * @return the opaque storage key (never containing directory paths or original client filenames)
     */
    public String store(MultipartFile file, String validatedExtension) {
        return storeWithUuidGenerator(file, validatedExtension, UUID::randomUUID);
    }

    String storeWithUuidGenerator(
            MultipartFile file,
            String validatedExtension,
            Supplier<UUID> uuidSupplier) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }

        String normalizedExtension = normalizeAndValidateExtension(validatedExtension);
        ensureStorageRootUsable();

        UUID uuid = Objects.requireNonNull(uuidSupplier.get(), "UUID generator returned null");
        String storageKey = uuid + "." + normalizedExtension;
        Path targetPath = resolveSafeStoragePath(storageKey);

        try (InputStream inputStream = file.getInputStream();
             OutputStream outputStream = Files.newOutputStream(
                     targetPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            inputStream.transferTo(outputStream);
            return storageKey;
        } catch (FileAlreadyExistsException ex) {
            log.error("Refused to overwrite existing attachment storage target for key {}", storageKey);
            throw new IllegalStateException("Failed to store attachment file due to storage key collision", ex);
        } catch (IOException | RuntimeException ex) {
            cleanupPartialFile(targetPath, storageKey);
            log.error("Failed to store attachment file under key {}", storageKey, ex);
            throw new IllegalStateException("Failed to store attachment file", ex);
        }
    }

    /**
     * Loads a stored attachment file as a Spring Resource after validating key format and path confinement.
     */
    public Resource loadAsResource(String storageKey) {
        Path targetPath = resolveSafeStoragePath(storageKey);

        if (!Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResourceNotFoundException("Attachment file not found");
        }

        if (Files.isSymbolicLink(targetPath)
                || !Files.isRegularFile(targetPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Invalid attachment storage target");
        }

        if (!Files.isReadable(targetPath)) {
            log.error("Attachment storage file is not readable for key {}", storageKey);
            throw new IllegalStateException("Attachment file is not readable");
        }

        return new FileSystemResource(targetPath);
    }

    /**
     * Deletes a stored attachment file if present. Missing files are treated idempotently.
     */
    public void deleteIfExists(String storageKey) {
        Path targetPath = resolveSafeStoragePath(storageKey);

        if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(targetPath)
                || !Files.isRegularFile(targetPath, LinkOption.NOFOLLOW_LINKS))) {
            throw new IllegalArgumentException("Invalid attachment storage target");
        }

        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException ex) {
            log.error("Failed to delete attachment file for key {}", storageKey, ex);
            throw new IllegalStateException("Failed to delete attachment file", ex);
        }
    }

    Path resolveSafeStoragePath(String storageKey) {
        validateStorageKeyFormat(storageKey);
        rejectSymbolicLinkStorageRoot();

        Path resolvedPath = storageRoot.resolve(storageKey).normalize();
        if (!Objects.equals(resolvedPath.getParent(), storageRoot) || !resolvedPath.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid attachment storage path");
        }
        return resolvedPath;
    }

    private void validateStorageKeyFormat(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }
        if (storageKey.indexOf('\0') >= 0
                || storageKey.contains("/")
                || storageKey.contains("\\")
                || storageKey.contains("..")
                || !STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("Invalid attachment storage key format");
        }
    }

    private String normalizeAndValidateExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new IllegalArgumentException("File extension must not be blank");
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported file extension: ." + normalized);
        }
        return normalized;
    }

    private void ensureStorageRootUsable() {
        rejectSymbolicLinkStorageRoot();

        try {
            Files.createDirectories(storageRoot);
        } catch (IOException ex) {
            log.error("Failed to initialize attachment storage directory", ex);
            throw new IllegalStateException("Attachment storage directory could not be initialized", ex);
        }

        rejectSymbolicLinkStorageRoot();

        if (!Files.isDirectory(storageRoot, LinkOption.NOFOLLOW_LINKS) || !Files.isWritable(storageRoot)) {
            log.error("Configured attachment storage directory is not a writable directory");
            throw new IllegalStateException("Attachment storage directory is not usable");
        }
    }

    private void rejectSymbolicLinkStorageRoot() {
        if (Files.isSymbolicLink(storageRoot)) {
            log.error("Configured attachment storage directory must not be a symbolic link");
            throw new IllegalStateException("Attachment storage directory must not be a symbolic link");
        }
    }

    private void cleanupPartialFile(Path targetPath, String storageKey) {
        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException cleanupEx) {
            log.warn("Failed to remove partial file after failed upload for key {}", storageKey, cleanupEx);
        }
    }
}

