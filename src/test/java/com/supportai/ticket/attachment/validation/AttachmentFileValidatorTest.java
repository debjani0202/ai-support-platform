package com.supportai.ticket.attachment.validation;

import com.supportai.ticket.attachment.config.AttachmentStorageProperties;
import com.supportai.ticket.attachment.validation.AttachmentFileValidator.ValidatedAttachment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttachmentFileValidatorTest {

    private static final int MAX_BYTES = 10_485_760;

    private static final byte[] PNG_HEADER = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final byte[] JPEG_HEADER = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0
    };

    private AttachmentFileValidator validator;

    @BeforeEach
    void setUp() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setMaxFileSizeBytes(MAX_BYTES);
        validator = new AttachmentFileValidator(properties);
    }

    @Test
    void validate_validPdf_succeedsWithCanonicalMetadata() {
        byte[] content = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", content);

        ValidatedAttachment result = validator.validate(file);

        assertEquals("invoice.pdf", result.originalFilename());
        assertEquals("pdf", result.extension());
        assertEquals("application/pdf", result.contentType());
        assertEquals(content.length, result.fileSize());
    }

    @Test
    void validate_validPng_succeedsWithCanonicalMetadata() {
        byte[] content = concat(PNG_HEADER, new byte[]{0x00, 0x00, 0x00, 0x0D});
        MockMultipartFile file = new MockMultipartFile("file", "screenshot.png", "image/png", content);

        ValidatedAttachment result = validator.validate(file);

        assertEquals("screenshot.png", result.originalFilename());
        assertEquals("png", result.extension());
        assertEquals("image/png", result.contentType());
        assertEquals(content.length, result.fileSize());
    }

    @Test
    void validate_validJpg_succeedsWithCanonicalMime() {
        byte[] content = concat(JPEG_HEADER, new byte[]{0x00, 0x10, 0x4A, 0x46});
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpg", content);

        ValidatedAttachment result = validator.validate(file);

        assertEquals("photo.jpg", result.originalFilename());
        assertEquals("jpg", result.extension());
        assertEquals("image/jpeg", result.contentType());
        assertEquals(content.length, result.fileSize());
    }

    @Test
    void validate_validJpeg_succeedsWithCanonicalMime() {
        byte[] content = concat(JPEG_HEADER, new byte[]{0x00, 0x10, 0x4A, 0x46});
        MockMultipartFile file = new MockMultipartFile("file", "camera.jpeg", "image/jpeg", content);

        ValidatedAttachment result = validator.validate(file);

        assertEquals("camera.jpeg", result.originalFilename());
        assertEquals("jpeg", result.extension());
        assertEquals("image/jpeg", result.contentType());
        assertEquals(content.length, result.fileSize());
    }

    @Test
    void validate_validUtf8Txt_withTabsNewlinesAndLiteralScriptText_succeeds() {
        String text = "Steps to reproduce:\r\n\t1. Observe <script>alert(1)</script>\nStatus: Résumé € ✓\n";
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain; charset=utf-8", content);

        ValidatedAttachment result = validator.validate(file);

        assertEquals("notes.txt", result.originalFilename());
        assertEquals("txt", result.extension());
        assertEquals("text/plain", result.contentType());
        assertEquals(content.length, result.fileSize());
    }

    @Test
    void validate_utf8MultiByteBoundaryAcrossBufferChunks_succeeds() {
        String prefix = "a".repeat(8190);
        String multiByte = "€€€€";
        byte[] content = (prefix + multiByte + " trailing line\n").getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "utf8-boundary.txt", "text/plain", content);

        ValidatedAttachment result = validator.validate(file);

        assertEquals("txt", result.extension());
        assertEquals(content.length, result.fileSize());
    }

    @Test
    void validate_uppercaseExtensions_preserveDisplayNameAndNormalizeExtension() {
        byte[] pngContent = concat(PNG_HEADER, new byte[]{0x01, 0x02});
        byte[] pdfContent = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);

        ValidatedAttachment pngResult = validator.validate(
                new MockMultipartFile("file", "IMAGE.PNG", "image/png", pngContent)
        );
        ValidatedAttachment pdfResult = validator.validate(
                new MockMultipartFile("file", "DOC.PDF", "application/pdf", pdfContent)
        );

        assertEquals("IMAGE.PNG", pngResult.originalFilename());
        assertEquals("png", pngResult.extension());
        assertEquals("image/png", pngResult.contentType());

        assertEquals("DOC.PDF", pdfResult.originalFilename());
        assertEquals("pdf", pdfResult.extension());
        assertEquals("application/pdf", pdfResult.contentType());
    }

    @Test
    void validate_missingOrUnsupportedExtension_rejected() {
        byte[] textBytes = "hello".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "README", "text/plain", textBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "README.", "text/plain", textBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", ".txt", "text/plain", textBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "payload.exe", "application/octet-stream", textBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "page.html", "text/html", textBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "icon.svg", "image/svg+xml", textBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "app.js", "application/javascript", textBytes)));
    }

    @Test
    void validate_nullEmptyOrZeroByteFile_rejected() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(null));

        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(emptyFile));

        MultipartFile zeroByteDeclared = new StreamingMultipartFile("zero.pdf", "application/pdf", 0L, new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(zeroByteDeclared));
    }

    @Test
    void validate_exactMaxSizeTenMibAccepted_andOneByteOverRejected() {
        byte[] exactMaxPdf = new byte[MAX_BYTES];
        byte[] prefix = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(prefix, 0, exactMaxPdf, 0, prefix.length);
        MockMultipartFile maxAllowedFile = new MockMultipartFile("file", "max.pdf", "application/pdf", exactMaxPdf);

        ValidatedAttachment accepted = validator.validate(maxAllowedFile);
        assertEquals(MAX_BYTES, accepted.fileSize());

        byte[] overMaxPdf = new byte[MAX_BYTES + 1];
        System.arraycopy(prefix, 0, overMaxPdf, 0, prefix.length);
        MockMultipartFile oversizedFile = new MockMultipartFile("file", "too-big.pdf", "application/pdf", overMaxPdf);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(oversizedFile));
    }

    @Test
    void validate_incorrectMagicBytesAndMasqueradedFormats_rejected() {
        byte[] pdfBytes = "%PDF-1.7\ncontent".getBytes(StandardCharsets.US_ASCII);
        byte[] pngBytes = concat(PNG_HEADER, new byte[]{0x01, 0x02});
        byte[] jpegBytes = concat(JPEG_HEADER, new byte[]{0x00, 0x10});
        byte[] plainBytes = "not a binary image".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "fake.pdf", "application/pdf", plainBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "fake.png", "image/png", plainBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "fake.jpg", "image/jpeg", plainBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "pdf-as-png.png", "image/png", pdfBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "png-as-jpg.jpg", "image/jpeg", pngBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "png-as-txt.txt", "text/plain", pngBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "jpg-as-txt.txt", "text/plain", jpegBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "pdf-as-txt.txt", "text/plain", pdfBytes)));
    }

    @Test
    void validate_executableAndArchiveSignatures_rejectedAcrossAllowedFormats() {
        byte[] mzBytes = new byte[]{'M', 'Z', (byte) 0x90, 0x00, 0x03};
        byte[] elfBytes = new byte[]{0x7F, 'E', 'L', 'F', 0x02, 0x01};
        byte[] zipBytes = new byte[]{'P', 'K', 0x03, 0x04, 0x14, 0x00};

        for (String name : new String[]{"evil.pdf", "evil.png", "evil.jpg", "evil.txt"}) {
            assertThrows(IllegalArgumentException.class, () ->
                    validator.validate(new MockMultipartFile("file", name, null, mzBytes)));
            assertThrows(IllegalArgumentException.class, () ->
                    validator.validate(new MockMultipartFile("file", name, null, elfBytes)));
            assertThrows(IllegalArgumentException.class, () ->
                    validator.validate(new MockMultipartFile("file", name, null, zipBytes)));
        }
    }

    @Test
    void validate_txtWithInvalidUtf8NulOrControlCharacters_rejected() {
        byte[] invalidUtf8 = new byte[]{(byte) 0xFF, (byte) 0xFE, 0x41, 0x42};
        byte[] nulContaining = new byte[]{'h', 'e', 'l', 0x00, 'o'};
        byte[] bellControl = new byte[]{'h', 'i', 0x07, '\n'};
        byte[] delControl = new byte[]{'h', 'i', 0x7F, '\n'};

        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "bad.txt", "text/plain", invalidUtf8)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "nul.txt", "text/plain", nulContaining)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "bell.txt", "text/plain", bellControl)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "del.txt", "text/plain", delControl)));
    }

    @Test
    void validate_unsafeFilenamesTraversalSeparatorsControlsAndLength_rejected() {
        byte[] pdfBytes = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        String over255Name = "a".repeat(252) + ".pdf";

        String[] rejectedNames = new String[]{
                null,
                "",
                "   ",
                "../secret.pdf",
                "..\\secret.pdf",
                "folder/secret.pdf",
                "folder\\secret.pdf",
                "file..pdf",
                "file\0.pdf",
                "file\nname.pdf",
                "file\tname.pdf",
                over255Name
        };

        for (String name : rejectedNames) {
            assertThrows(IllegalArgumentException.class, () ->
                    validator.validate(new MockMultipartFile("file", name, "application/pdf", pdfBytes)),
                    "Expected rejection for filename: " + name);
        }
    }

    @Test
    void validate_mimeHeaderRules_rejectsIncompatibleAndAllowsOctetStreamOrMissingWhenContentValid() {
        byte[] pdfBytes = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        byte[] pngBytes = concat(PNG_HEADER, new byte[]{0x00, 0x01});

        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "doc.pdf", "text/html", pdfBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "doc.pdf", "image/png", pdfBytes)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "pic.png", "image/svg+xml", pngBytes)));

        ValidatedAttachment octetPdf = validator.validate(
                new MockMultipartFile("file", "doc.pdf", "application/octet-stream", pdfBytes)
        );
        ValidatedAttachment missingMimePng = validator.validate(
                new MockMultipartFile("file", "pic.png", null, pngBytes)
        );

        assertEquals("application/pdf", octetPdf.contentType());
        assertEquals("image/png", missingMimePng.contentType());
    }

    @Test
    void validate_shortTxtWithPdfOrMzSignature_rejected() {
        byte[] shortPdf = "%PDF-\n".getBytes(StandardCharsets.US_ASCII);
        byte[] shortPdfVersion = "%PDF-1\n".getBytes(StandardCharsets.US_ASCII);
        byte[] shortMz = "MZ".getBytes(StandardCharsets.US_ASCII);
        byte[] shortMzAscii = "MZabc".getBytes(StandardCharsets.US_ASCII);

        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "tiny-pdf.txt", "text/plain", shortPdf)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "tiny-pdf-v.txt", "text/plain", shortPdfVersion)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "tiny-mz.txt", "text/plain", shortMz)));
        assertThrows(IllegalArgumentException.class, () ->
                validator.validate(new MockMultipartFile("file", "tiny-mz-ascii.txt", "text/plain", shortMzAscii)));
    }

    @Test
    void validate_chunkedSmallReadsTxtWithBinarySignatureRejected_andValidChunkedUtf8Accepted() {
        byte[] asciiPdf = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);
        byte[] asciiMz = "MZ_NOT_EXECUTABLE_ASCII_TEXT".getBytes(StandardCharsets.US_ASCII);

        MultipartFile chunkedPdfTxt = new ChunkedMultipartFile("trickle-pdf.txt", "text/plain", asciiPdf, 2);
        MultipartFile chunkedMzTxt = new ChunkedMultipartFile("trickle-mz.txt", "text/plain", asciiMz, 1);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(chunkedPdfTxt));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(chunkedMzTxt));

        byte[] shortValidText = "OK €✓".getBytes(StandardCharsets.UTF_8);
        ValidatedAttachment shortAccepted = validator.validate(
                new MockMultipartFile("file", "short.txt", "text/plain", shortValidText)
        );
        assertEquals(shortValidText.length, shortAccepted.fileSize());

        byte[] chunkedValidUtf8 = "Hello € world ✓\nSecond line\n".getBytes(StandardCharsets.UTF_8);
        ValidatedAttachment chunkedAccepted = validator.validate(
                new ChunkedMultipartFile("chunked.txt", "text/plain", chunkedValidUtf8, 2)
        );
        assertEquals(chunkedValidUtf8.length, chunkedAccepted.fileSize());
    }

    private byte[] concat(byte[] first, byte[] second) {
        byte[] combined = new byte[first.length + second.length];
        System.arraycopy(first, 0, combined, 0, first.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    private static final class StreamingMultipartFile implements MultipartFile {
        private final String originalFilename;
        private final String contentType;
        private final long size;
        private final byte[] bytes;

        private StreamingMultipartFile(String originalFilename, String contentType, long size, byte[] bytes) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.size = size;
            this.bytes = bytes;
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
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return size <= 0;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ChunkedMultipartFile implements MultipartFile {
        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;
        private final int maxBytesPerRead;

        private ChunkedMultipartFile(String originalFilename, String contentType, byte[] bytes, int maxBytesPerRead) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.bytes = bytes;
            this.maxBytesPerRead = maxBytesPerRead;
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
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new InputStream() {
                private int offset = 0;

                @Override
                public int read() {
                    if (offset >= bytes.length) {
                        return -1;
                    }
                    return bytes[offset++] & 0xFF;
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    if (offset >= bytes.length) {
                        return -1;
                    }
                    int count = Math.min(Math.min(len, maxBytesPerRead), bytes.length - offset);
                    System.arraycopy(bytes, offset, b, off, count);
                    offset += count;
                    return count;
                }
            };
        }

        @Override
        public void transferTo(File dest) {
            throw new UnsupportedOperationException();
        }
    }
}

