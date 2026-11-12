package com.supportai.ticket.attachment.validation;

import com.supportai.ticket.attachment.config.AttachmentStorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure Java validator for uploaded ticket attachments.
 * Treats client filenames and declared MIME types as untrusted,
 * enforcing extension allowlists, size bounds, and magic-byte/content inspection.
 */
@Component
public class AttachmentFileValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "txt");
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final int STREAM_BUFFER_SIZE = 8192;
    private static final String GENERIC_BINARY_MIME = "application/octet-stream";

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] JPEG_SIGNATURE = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
    };
    private static final byte[] MZ_SIGNATURE = new byte[]{'M', 'Z'};
    private static final byte[] ELF_SIGNATURE = new byte[]{0x7F, 'E', 'L', 'F'};
    private static final byte[] ZIP_LOCAL_HEADER = new byte[]{'P', 'K', 0x03, 0x04};
    private static final byte[] ZIP_EMPTY_ARCHIVE = new byte[]{'P', 'K', 0x05, 0x06};
    private static final byte[] ZIP_SPANNED_ARCHIVE = new byte[]{'P', 'K', 0x07, 0x08};

    private static final Map<String, String> CANONICAL_MIME_BY_EXT = Map.of(
            "pdf", "application/pdf",
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "txt", "text/plain"
    );

    private static final Map<String, Set<String>> COMPATIBLE_DECLARED_MIMES = Map.of(
            "pdf", Set.of("application/pdf"),
            "png", Set.of("image/png"),
            "jpg", Set.of("image/jpeg", "image/jpg", "image/pjpeg"),
            "jpeg", Set.of("image/jpeg", "image/jpg", "image/pjpeg"),
            "txt", Set.of("text/plain")
    );

    private final AttachmentStorageProperties storageProperties;

    public AttachmentFileValidator(AttachmentStorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    /**
     * Validated attachment metadata ready for storage and persistence.
     */
    public record ValidatedAttachment(
            String originalFilename,
            String extension,
            String contentType,
            long fileSize
    ) {
    }

    /**
     * Validates an uploaded MultipartFile and returns sanitized metadata with canonical MIME type.
     */
    public ValidatedAttachment validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }

        long declaredSize = file.getSize();
        long maxFileSizeBytes = storageProperties.getMaxFileSizeBytes();
        if (declaredSize > maxFileSizeBytes) {
            throw new IllegalArgumentException(
                    "Uploaded file exceeds the maximum allowed size of " + maxFileSizeBytes + " bytes"
            );
        }

        String sanitizedFilename = validateAndSanitizeFilename(file.getOriginalFilename());
        String extension = extractNormalizedExtension(sanitizedFilename);
        validateDeclaredMimeType(file.getContentType(), extension);

        long inspectedSize = validateContentByExtension(file, extension, maxFileSizeBytes);
        String canonicalMime = CANONICAL_MIME_BY_EXT.get(extension);

        return new ValidatedAttachment(sanitizedFilename, extension, canonicalMime, inspectedSize);
    }

    private String validateAndSanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            throw new IllegalArgumentException("Filename must not be blank");
        }

        if (rawFilename.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Filename contains illegal NUL character");
        }

        for (int i = 0; i < rawFilename.length(); i++) {
            char ch = rawFilename.charAt(i);
            if (Character.isISOControl(ch) || isBidiOverrideControl(ch)) {
                throw new IllegalArgumentException("Filename contains illegal control characters");
            }
        }

        String trimmed = rawFilename.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_FILENAME_LENGTH) {
            throw new IllegalArgumentException("Filename must be between 1 and 255 characters");
        }

        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
            throw new IllegalArgumentException("Filename must not contain path separators or traversal sequences");
        }

        int lastDot = trimmed.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == trimmed.length() - 1) {
            throw new IllegalArgumentException("Filename must include a valid file extension");
        }

        return trimmed;
    }

    private boolean isBidiOverrideControl(char ch) {
        return (ch >= '\u202A' && ch <= '\u202E') || (ch >= '\u2066' && ch <= '\u2069');
    }

    private String extractNormalizedExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        String extension = filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported file extension: ." + extension);
        }
        return extension;
    }

    private void validateDeclaredMimeType(String rawContentType, String extension) {
        if (rawContentType == null || rawContentType.isBlank()) {
            return;
        }

        String normalizedMime = normalizeMimeType(rawContentType);
        if (normalizedMime.isEmpty() || GENERIC_BINARY_MIME.equals(normalizedMime)) {
            return;
        }

        Set<String> allowedMimes = COMPATIBLE_DECLARED_MIMES.get(extension);
        if (allowedMimes == null || !allowedMimes.contains(normalizedMime)) {
            throw new IllegalArgumentException(
                    "Declared MIME type '" + normalizedMime + "' does not match extension ." + extension
            );
        }
    }

    private String normalizeMimeType(String rawContentType) {
        int semicolonIndex = rawContentType.indexOf(';');
        String baseMime = semicolonIndex >= 0
                ? rawContentType.substring(0, semicolonIndex)
                : rawContentType;
        return baseMime.trim().toLowerCase(Locale.ROOT);
    }

    private long validateContentByExtension(MultipartFile file, String extension, long maxFileSizeBytes) {
        try (InputStream rawStream = file.getInputStream();
             BufferedInputStream bufferedIn = new BufferedInputStream(rawStream, STREAM_BUFFER_SIZE)) {
            return switch (extension) {
                case "pdf" -> validateMagicHeader(bufferedIn, PDF_SIGNATURE, "Invalid PDF file signature", file.getSize());
                case "png" -> validateMagicHeader(bufferedIn, PNG_SIGNATURE, "Invalid PNG file signature", file.getSize());
                case "jpg", "jpeg" -> validateMagicHeader(bufferedIn, JPEG_SIGNATURE, "Invalid JPEG file signature", file.getSize());
                case "txt" -> validateUtf8TextStream(bufferedIn, maxFileSizeBytes);
                default -> throw new IllegalArgumentException("Unsupported file extension: ." + extension);
            };
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read uploaded file content", ex);
        }
    }

    private long validateMagicHeader(
            InputStream inputStream,
            byte[] expectedSignature,
            String errorMessage,
            long declaredSize) throws IOException {
        byte[] header = inputStream.readNBytes(expectedSignature.length);
        if (header.length < expectedSignature.length || !Arrays.equals(header, expectedSignature)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return declaredSize;
    }

    private long validateUtf8TextStream(InputStream inputStream, long maxFileSizeBytes) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        byte[] buffer = new byte[STREAM_BUFFER_SIZE];
        byte[] headerPrefix = new byte[8];
        CharBuffer charBuffer = CharBuffer.allocate(STREAM_BUFFER_SIZE);

        int leftover = 0;
        int headerPrefixLength = 0;
        long totalBytes = 0;
        boolean signatureChecked = false;

        while (true) {
            int bytesRead = inputStream.read(buffer, leftover, buffer.length - leftover);
            boolean endOfInput = bytesRead == -1;

            if (!endOfInput) {
                totalBytes += bytesRead;
                if (totalBytes > maxFileSizeBytes) {
                    throw new IllegalArgumentException(
                            "Uploaded file exceeds the maximum allowed size of " + maxFileSizeBytes + " bytes"
                    );
                }

                if (headerPrefixLength < headerPrefix.length && bytesRead > 0) {
                    int toCopy = Math.min(headerPrefix.length - headerPrefixLength, bytesRead);
                    System.arraycopy(buffer, leftover, headerPrefix, headerPrefixLength, toCopy);
                    headerPrefixLength += toCopy;
                }
            }

            int validLength = endOfInput ? leftover : leftover + bytesRead;

            if (!signatureChecked && (headerPrefixLength == headerPrefix.length || endOfInput)) {
                rejectKnownBinarySignatures(headerPrefix, headerPrefixLength);
                signatureChecked = true;
            }

            if (validLength > 0 || endOfInput) {
                ByteBuffer byteChunk = ByteBuffer.wrap(buffer, 0, validLength);
                leftover = decodeChunkAndValidateCharacters(decoder, byteChunk, charBuffer, endOfInput);
            }

            if (endOfInput) {
                break;
            }
        }

        if (totalBytes <= 0) {
            throw new IllegalArgumentException("Uploaded text file must not be empty");
        }

        return totalBytes;
    }

    private void rejectKnownBinarySignatures(byte[] bytes, int length) {
        if (startsWith(bytes, length, MZ_SIGNATURE)
                || startsWith(bytes, length, ELF_SIGNATURE)
                || startsWith(bytes, length, ZIP_LOCAL_HEADER)
                || startsWith(bytes, length, ZIP_EMPTY_ARCHIVE)
                || startsWith(bytes, length, ZIP_SPANNED_ARCHIVE)
                || startsWith(bytes, length, PDF_SIGNATURE)
                || startsWith(bytes, length, PNG_SIGNATURE)
                || startsWith(bytes, length, JPEG_SIGNATURE)) {
            throw new IllegalArgumentException("Text file cannot contain a binary or executable file signature");
        }
    }

    private boolean startsWith(byte[] buffer, int validLength, byte[] signature) {
        if (validLength < signature.length) {
            return false;
        }
        return Arrays.equals(buffer, 0, signature.length, signature, 0, signature.length);
    }

    private int decodeChunkAndValidateCharacters(
            CharsetDecoder decoder,
            ByteBuffer byteChunk,
            CharBuffer charBuffer,
            boolean endOfInput) {
        while (true) {
            charBuffer.clear();
            CoderResult result = decoder.decode(byteChunk, charBuffer, endOfInput);
            validateDecodedChars(charBuffer);

            if (result.isMalformed() || result.isUnmappable()) {
                throw new IllegalArgumentException("Text file contains invalid UTF-8 byte sequences");
            }

            if (result.isOverflow()) {
                continue;
            }

            if (endOfInput) {
                flushAndValidateCharacters(decoder, charBuffer);
                return 0;
            }

            int remaining = byteChunk.remaining();
            if (remaining > 0) {
                byte[] backingArray = byteChunk.array();
                System.arraycopy(backingArray, byteChunk.position(), backingArray, 0, remaining);
            }
            return remaining;
        }
    }

    private void flushAndValidateCharacters(CharsetDecoder decoder, CharBuffer charBuffer) {
        while (true) {
            charBuffer.clear();
            CoderResult flushResult = decoder.flush(charBuffer);
            validateDecodedChars(charBuffer);
            if (flushResult.isMalformed() || flushResult.isUnmappable()) {
                throw new IllegalArgumentException("Text file contains incomplete or invalid UTF-8 sequences");
            }
            if (!flushResult.isOverflow()) {
                break;
            }
        }
    }

    private void validateDecodedChars(CharBuffer charBuffer) {
        charBuffer.flip();
        while (charBuffer.hasRemaining()) {
            char ch = charBuffer.get();
            if (ch == '\0') {
                throw new IllegalArgumentException("Text file must not contain NUL bytes");
            }
            if (isDisallowedControlCharacter(ch)) {
                throw new IllegalArgumentException("Text file contains disallowed control characters");
            }
        }
    }

    private boolean isDisallowedControlCharacter(char ch) {
        if (ch == '\t' || ch == '\n' || ch == '\r') {
            return false;
        }
        return ch < 0x20 || ch == 0x7F || (ch >= 0x80 && ch <= 0x9F);
    }
}

