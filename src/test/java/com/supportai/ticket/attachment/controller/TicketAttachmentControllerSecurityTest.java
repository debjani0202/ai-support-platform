package com.supportai.ticket.attachment.controller;

import com.supportai.exception.ConflictException;
import com.supportai.exception.ForbiddenException;
import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.security.SecurityConfig;
import com.supportai.ticket.attachment.dto.AttachmentDownload;
import com.supportai.ticket.attachment.dto.AttachmentResponse;
import com.supportai.ticket.attachment.service.TicketAttachmentService;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.user.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketAttachmentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TicketAttachmentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketAttachmentService attachmentService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void unauthenticatedRequests_return401ForAllAttachmentEndpoints() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/tickets/1/attachments").file(file))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets/1/attachments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets/1/attachments/5/download"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/tickets/1/attachments/5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadAttachment_returns201AndDoesNotExposeStoredFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", "%PDF-1.7\ncontent".getBytes(StandardCharsets.US_ASCII)
        );
        AttachmentResponse response = new AttachmentResponse(
                10L, 1L, 7L, "Alice", "invoice.pdf", "application/pdf", 15L, LocalDateTime.now()
        );

        when(attachmentService.uploadAttachment(eq(1L), any(), eq(7L), eq(Role.CUSTOMER)))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/tickets/1/attachments")
                        .file(file)
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.ticketId").value(1))
                .andExpect(jsonPath("$.uploadedById").value(7))
                .andExpect(jsonPath("$.uploadedByName").value("Alice"))
                .andExpect(jsonPath("$.originalFileName").value("invoice.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.fileSize").value(15))
                .andExpect(jsonPath("$.storedFilename").doesNotExist());
    }

    @Test
    void uploadAttachment_missingPartOrMalformedMultipart_returns400() throws Exception {
        MockMultipartFile wrongPart = new MockMultipartFile("attachment", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/tickets/1/attachments")
                        .file(wrongPart)
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Required upload part 'file' is missing."));

        mockMvc.perform(post("/api/tickets/1/attachments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed multipart upload request."));
    }

    @Test
    void uploadAttachment_maxUploadSizeAndWrappedMultipartExceptions_return413And400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));

        when(attachmentService.uploadAttachment(eq(1L), any(), eq(7L), eq(Role.CUSTOMER)))
                .thenThrow(new MaxUploadSizeExceededException(10_485_760L))
                .thenThrow(new MultipartException("Wrapped size overflow", new MaxUploadSizeExceededException(10_485_760L)))
                .thenThrow(new MultipartException("Corrupted multipart payload"));

        mockMvc.perform(multipart("/api/tickets/1/attachments")
                        .file(file)
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.error").value("Payload Too Large"))
                .andExpect(jsonPath("$.message").value("Uploaded file exceeds the maximum allowed size of 10 MB."));

        mockMvc.perform(multipart("/api/tickets/1/attachments")
                        .file(file)
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.status").value(413));

        mockMvc.perform(multipart("/api/tickets/1/attachments")
                        .file(file)
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed multipart upload request."));
    }

    @Test
    void uploadAttachment_propagates400_403_404_409() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        when(attachmentService.uploadAttachment(eq(1L), any(), eq(7L), eq(Role.CUSTOMER)))
                .thenThrow(new IllegalArgumentException("Unsupported file extension"))
                .thenThrow(new ForbiddenException("You are not allowed to access this ticket"))
                .thenThrow(new ResourceNotFoundException("Ticket not found with ID: 1"))
                .thenThrow(new ConflictException("Ticket has reached the maximum limit of 20 attachments"));

        mockMvc.perform(multipart("/api/tickets/1/attachments")
                        .file(file)
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(multipart("/api/tickets/1/attachments")
                        .file(file)
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(multipart("/api/tickets/1/attachments")
                        .file(file)
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(multipart("/api/tickets/1/attachments")
                        .file(file)
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void getAttachments_supportsPaginationAndPropagatesErrors() throws Exception {
        AttachmentResponse attachment = new AttachmentResponse(
                3L, 1L, 7L, "Alice", "screenshot.png", "image/png", 512L, LocalDateTime.now()
        );
        PageResponse<AttachmentResponse> page = new PageResponse<>(
                List.of(attachment), 1, 5, 6, 2, false, true
        );

        when(attachmentService.getAttachments(eq(1L), eq(7L), eq(Role.CUSTOMER), eq(1), eq(5)))
                .thenReturn(page);
        when(attachmentService.getAttachments(eq(1L), eq(7L), eq(Role.CUSTOMER), eq(-1), eq(20)))
                .thenThrow(new IllegalArgumentException("Page number cannot be negative"));

        mockMvc.perform(get("/api/tickets/1/attachments")
                        .param("page", "1")
                        .param("size", "5")
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.content[0].originalFileName").value("screenshot.png"))
                .andExpect(jsonPath("$.content[0].storedFilename").doesNotExist())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));

        mockMvc.perform(get("/api/tickets/1/attachments")
                        .param("page", "-1")
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadAttachment_returnsBytesAndRequiredSecurityHeaders() throws Exception {
        byte[] content = "hello utf8 ✓".getBytes(StandardCharsets.UTF_8);
        AttachmentDownload download = new AttachmentDownload(
                new ByteArrayResource(content),
                "résumé notes.txt",
                "text/plain",
                content.length
        );

        when(attachmentService.downloadAttachment(eq(1L), eq(5L), eq(2L), eq(Role.AGENT)))
                .thenReturn(download);
        when(attachmentService.downloadAttachment(eq(1L), eq(99L), eq(2L), eq(Role.AGENT)))
                .thenThrow(new ResourceNotFoundException("Attachment not found with ID: 99"));

        mockMvc.perform(get("/api/tickets/1/attachments/5/download")
                        .with(jwt().jwt(jwt -> jwt.subject("2").claim("role", "AGENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AGENT"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain"))
                .andExpect(content().bytes(content))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, content.length))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment;")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("UTF-8''")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));

        mockMvc.perform(get("/api/tickets/1/attachments/99/download")
                        .with(jwt().jwt(jwt -> jwt.subject("2").claim("role", "AGENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AGENT"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAttachment_returns204AndPropagates403_404_409() throws Exception {
        doNothing().when(attachmentService).deleteAttachment(eq(1L), eq(5L), eq(1L), eq(Role.ADMIN));
        doThrow(new ForbiddenException("You are only allowed to delete your own attachments"))
                .when(attachmentService).deleteAttachment(eq(1L), eq(6L), eq(7L), eq(Role.CUSTOMER));
        doThrow(new ResourceNotFoundException("Attachment not found with ID: 99"))
                .when(attachmentService).deleteAttachment(eq(1L), eq(99L), eq(7L), eq(Role.CUSTOMER));
        doThrow(new ConflictException("Cannot delete attachments from a closed ticket"))
                .when(attachmentService).deleteAttachment(eq(1L), eq(7L), eq(7L), eq(Role.CUSTOMER));

        mockMvc.perform(delete("/api/tickets/1/attachments/5")
                        .with(jwt().jwt(jwt -> jwt.subject("1").claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/tickets/1/attachments/6")
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/tickets/1/attachments/99")
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/tickets/1/attachments/7")
                        .with(jwt().jwt(jwt -> jwt.subject("7").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isConflict());
    }
}

