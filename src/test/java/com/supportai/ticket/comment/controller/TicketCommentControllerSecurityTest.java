package com.supportai.ticket.comment.controller;

import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.security.SecurityConfig;
import com.supportai.ticket.comment.dto.CommentResponse;
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
    void authenticatedCustomerCanGetComments() throws Exception {
        when(commentService.getComments(eq(1L), eq(1L), eq(Role.CUSTOMER)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/tickets/1/comments")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk());
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
        when(commentService.getComments(eq(1L), eq(1L), eq(Role.CUSTOMER)))
                .thenThrow(new ResourceNotFoundException("Ticket not found with ID: 1"));

        mockMvc.perform(get("/api/tickets/1/comments")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("1")
                                .claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound());
    }
}
