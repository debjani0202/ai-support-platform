package com.supportai.ticket.attachment.service;

import com.supportai.exception.ResourceNotFoundException;
import com.supportai.ticket.attachment.config.AttachmentStorageProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentStorageServiceTest {

    @TempDir
    Path tempDir;

    private Path storageRoot;
    private AttachmentStorageService storageService;

    @BeforeEach
    void setUp() {
        storageRoot = tempDir.resolve("attachments-root").toAbsolutePath().normalize();
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setStorageDir(storageRoot.toString());
        storageService = new AttachmentStorageService(properties);
    }

    @Test
    void store_createsUuidFileInsideConfiguredRoot_andIgnoresOriginalFilename() throws Exception {
        byte[] content = "%PDF-1.7\nsample pdf payload".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "client-secret-name.pdf", "application/pdf", content);

        String storageKey = storageService.store(file, "pdf");

        assertTrue(storageKey.matches("^[0-9a-f-]{36}\\.pdf$"));
        assertFalse(storageKey.contains("client-secret-name"));

        Path storedFile = storageRoot.resolve(storageKey);
        assertTrue(Files.exists(storedFile));
        assertEquals(storageRoot, storedFile.toAbsolutePath().normalize().getParent());
        assertArrayEquals(content, Files.readAllBytes(storedFile));
    }

    @Test
    void store_twoUploadsProduceDistinctStorageKeys() {
        byte[] content = "identical text content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile first = new MockMultipartFile("file", "same.txt", "text/plain", content);
        MockMultipartFile second = new MockMultipartFile("file", "same.txt", "text/plain", content);

        String keyOne = storageService.store(first, "txt");
        String keyTwo = storageService.store(second, "txt");

        assertNotEquals(keyOne, keyTwo);
        assertTrue(Files.exists(storageRoot.resolve(keyOne)));
        assertTrue(Files.exists(storageRoot.resolve(keyTwo)));
    }

    @Test
    void loadAsResource_returnsReadableResourceWithOriginalBytes() throws Exception {
        byte[] content = "hello attachment download".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", content);
        String storageKey = storageService.store(file, "txt");

        Resource resource = storageService.loadAsResource(storageKey);

        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
        try (InputStream in = resource.getInputStream()) {
            assertArrayEquals(content, in.readAllBytes());
        }
    }

    @Test
    void loadAsResource_missingFile_throwsResourceNotFound() {
        String missingKey = UUID.randomUUID() + ".pdf";
        assertThrows(ResourceNotFoundException.class, () -> storageService.loadAsResource(missingKey));
    }

    @Test
    void deleteIfExists_deletesExistingFile_andRemainsIdempotentWhenMissing() {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "to delete".getBytes(StandardCharsets.UTF_8));
        String storageKey = storageService.store(file, "txt");
        Path storedPath = storageRoot.resolve(storageKey);
        assertTrue(Files.exists(storedPath));

        storageService.deleteIfExists(storageKey);
        assertFalse(Files.exists(storedPath));

        storageService.deleteIfExists(storageKey);
        assertFalse(Files.exists(storedPath));
    }

    @Test
    void loadAndDelete_rejectPathTraversalAndInvalidStorageKeys() {
        String validUuid = UUID.randomUUID().toString();
        String[] invalidKeys = new String[]{
                null,
                "",
                "   ",
                "../" + validUuid + ".pdf",
                "..\\" + validUuid + ".pdf",
                "nested/" + validUuid + ".pdf",
                "nested\\" + validUuid + ".pdf",
                validUuid + ".exe",
                validUuid,
                "not-a-uuid.pdf",
                validUuid + "\0.pdf"
        };

        for (String key : invalidKeys) {
            assertThrows(IllegalArgumentException.class, () -> storageService.loadAsResource(key));
            assertThrows(IllegalArgumentException.class, () -> storageService.deleteIfExists(key));
        }
    }

    @Test
    void storeWithUuidGenerator_doesNotOverwriteExistingFile() throws Exception {
        UUID fixedUuid = UUID.randomUUID();
        byte[] initialBytes = "original immutable content".getBytes(StandardCharsets.UTF_8);
        byte[] competingBytes = "attacker overwrite attempt".getBytes(StandardCharsets.UTF_8);

        MockMultipartFile first = new MockMultipartFile("file", "a.txt", "text/plain", initialBytes);
        MockMultipartFile second = new MockMultipartFile("file", "b.txt", "text/plain", competingBytes);

        String key = storageService.storeWithUuidGenerator(first, "txt", () -> fixedUuid);

        assertThrows(IllegalStateException.class, () ->
                storageService.storeWithUuidGenerator(second, "txt", () -> fixedUuid));

        assertArrayEquals(initialBytes, Files.readAllBytes(storageRoot.resolve(key)));
    }

    @Test
    void store_partialFailureDuringStreamWrite_cleansUpPartialFile() throws Exception {
        MultipartFile failingFile = new PartialFailingMultipartFile(
                "broken.txt",
                "partial bytes before crash".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(IllegalStateException.class, () -> storageService.store(failingFile, "txt"));

        assertTrue(Files.exists(storageRoot));
        try (var files = Files.list(storageRoot)) {
            assertEquals(0L, files.count());
        }
    }

    @Test
    void loadAndDelete_rejectNonRegularFileTarget() throws Exception {
        Files.createDirectories(storageRoot);
        String directoryKey = UUID.randomUUID() + ".txt";
        Files.createDirectories(storageRoot.resolve(directoryKey));

        assertThrows(IllegalArgumentException.class, () -> storageService.loadAsResource(directoryKey));
        assertThrows(IllegalArgumentException.class, () -> storageService.deleteIfExists(directoryKey));
    }

    @Test
    void storageRootSymbolicLink_rejectedBeforeCreatingOrWritingFiles() throws Exception {
        Path realTargetDir = Files.createDirectories(tempDir.resolve("real-target-dir"));
        String existingKey = UUID.randomUUID() + ".txt";
        Path existingFile = Files.writeString(realTargetDir.resolve(existingKey), "existing");
        Path symlinkRoot = tempDir.resolve("symlink-storage-root");

        try {
            Files.createSymbolicLink(symlinkRoot, realTargetDir);
        } catch (UnsupportedOperationException | SecurityException | IOException ex) {
            Assumptions.abort("Symbolic link creation is not supported in this test environment: " + ex.getMessage());
        }

        AttachmentStorageProperties symlinkProperties = new AttachmentStorageProperties();
        symlinkProperties.setStorageDir(symlinkRoot.toString());
        AttachmentStorageService symlinkService = new AttachmentStorageService(symlinkProperties);

        MockMultipartFile upload = new MockMultipartFile(
                "file", "note.txt", "text/plain", "should not be written".getBytes(StandardCharsets.UTF_8)
        );

        IllegalStateException storeException = assertThrows(
                IllegalStateException.class,
                () -> symlinkService.store(upload, "txt")
        );
        assertTrue(storeException.getMessage().contains("symbolic link"));

        assertThrows(IllegalStateException.class, () -> symlinkService.loadAsResource(existingKey));
        assertThrows(IllegalStateException.class, () -> symlinkService.deleteIfExists(existingKey));

        assertTrue(Files.exists(existingFile));
        try (var files = Files.list(realTargetDir)) {
            assertEquals(1L, files.count());
        }

        Path danglingTarget = tempDir.resolve("missing-target-dir");
        Path danglingSymlinkRoot = tempDir.resolve("dangling-symlink-storage-root");
        Files.createSymbolicLink(danglingSymlinkRoot, danglingTarget);

        AttachmentStorageProperties danglingProperties = new AttachmentStorageProperties();
        danglingProperties.setStorageDir(danglingSymlinkRoot.toString());
        AttachmentStorageService danglingService = new AttachmentStorageService(danglingProperties);

        assertThrows(IllegalStateException.class, () -> danglingService.store(upload, "txt"));
        assertFalse(Files.exists(danglingTarget));
    }

    private static final class PartialFailingMultipartFile implements MultipartFile {
        private final String originalFilename;
        private final byte[] prefix;

        private PartialFailingMultipartFile(String originalFilename, byte[] prefix) {
            this.originalFilename = originalFilename;
            this.prefix = prefix;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return "text/plain";
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public long getSize() {
            return prefix.length + 100L;
        }

        @Override
        public byte[] getBytes() {
            return prefix;
        }

        @Override
        public InputStream getInputStream() {
            return new InputStream() {
                private int index = 0;

                @Override
                public int read() throws IOException {
                    if (index < prefix.length) {
                        return prefix[index++] & 0xFF;
                    }
                    throw new IOException("Simulated mid-stream IO failure");
                }
            };
        }

        @Override
        public void transferTo(File dest) {
            throw new UnsupportedOperationException();
        }
    }
}

