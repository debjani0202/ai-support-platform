package com.supportai.ticket.comment.controller;

import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.security.SecurityConfig;
import com.supportai.exception.ForbiddenException;
import com.supportai.ticket.comment.dto.CommentResponse;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.ticket.comment.dto.CreateCommentRequest;
import com.supportai.ticket.comment.service.TicketCommentService;
import com.supportai.user.entity.Role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketCommentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TicketCommentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketCommentService commentService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void unauthenticatedUserCannotGetComments() throws Exception {
        mockMvc.perform(get("/api/tickets/1/comments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedUserCannotAddComment() throws Exception {
        mockMvc.perform(post("/api/tickets/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "content": "Valid comment content"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCustomerCanGetComments_withDefaultPagination() throws Exception {
        PageResponse<CommentResponse> emptyPage = new PageResponse<>(
                List.of(), 0, 20, 0, 0, true, true
        );

        when(commentService.getComments(eq(1L), eq(1L), eq(Role.CUSTOMER), eq(0), eq(20)))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/tickets/1/comments")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void getComments_withExplicitPaginationParameters_forwardsToService() throws Exception {
        CommentResponse comment = new CommentResponse(
                5L, 1L, 1L, "Author", "Comment text", LocalDateTime.now()
        );
        PageResponse<CommentResponse> pageResponse = new PageResponse<>(
                List.of(comment), 1, 10, 15, 2, false, false
        );

        when(commentService.getComments(eq(1L), eq(1L), eq(Role.CUSTOMER), eq(1), eq(10)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/tickets/1/comments")
                        .param("page", "1")
                        .param("size", "10")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void getComments_withNegativePage_returns400() throws Exception {
        when(commentService.getComments(eq(1L), eq(1L), eq(Role.CUSTOMER), eq(-1), eq(20)))
                .thenThrow(new IllegalArgumentException("Page number cannot be negative"));

        mockMvc.perform(get("/api/tickets/1/comments")
                        .param("page", "-1")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getComments_withZeroSize_returns400() throws Exception {
        when(commentService.getComments(eq(1L), eq(1L), eq(Role.CUSTOMER), eq(0), eq(0)))
                .thenThrow(new IllegalArgumentException("Page size must be between 1 and 100"));

        mockMvc.perform(get("/api/tickets/1/comments")
                        .param("size", "0")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getComments_withSizeExceeding100_returns400() throws Exception {
        when(commentService.getComments(eq(1L), eq(1L), eq(Role.CUSTOMER), eq(0), eq(101)))
                .thenThrow(new IllegalArgumentException("Page size must be between 1 and 100"));

        mockMvc.perform(get("/api/tickets/1/comments")
                        .param("size", "101")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getComments_forbiddenCustomer_returns403() throws Exception {
        when(commentService.getComments(eq(1L), eq(2L), eq(Role.CUSTOMER), eq(0), eq(20)))
                .thenThrow(new ForbiddenException("You do not have access to this ticket"));

        mockMvc.perform(get("/api/tickets/1/comments")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("2")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedCustomerCanAddComment() throws Exception {
        CommentResponse response = new CommentResponse(
                1L, 1L, 1L, "Test Author", "Valid content", LocalDateTime.now()
        );

        when(commentService.addComment(eq(1L), any(CreateCommentRequest.class), eq(1L), eq(Role.CUSTOMER)))
                .thenReturn(response);

        mockMvc.perform(post("/api/tickets/1/comments")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "content": "Valid comment content"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void addComment_returns404WhenTicketSoftDeleted() throws Exception {
        when(commentService.addComment(eq(1L), any(CreateCommentRequest.class), eq(1L), eq(Role.CUSTOMER)))
                .thenThrow(new ResourceNotFoundException("Ticket not found with ID: 1"));

        mockMvc.perform(post("/api/tickets/1/comments")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "content": "Valid comment content"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void getComments_returns404WhenTicketSoftDeleted() throws Exception {
        when(commentService.getComments(eq(1L), eq(1L), eq(Role.CUSTOMER), eq(0), eq(20)))
                .thenThrow(new ResourceNotFoundException("Ticket not found with ID: 1"));

        mockMvc.perform(get("/api/tickets/1/comments")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound());
    }
}
