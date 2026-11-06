package com.supportai.user.controller;

import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.security.SecurityConfig;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.user.dto.AgentRequestResponse;
import com.supportai.user.entity.AgentRequestStatus;
import com.supportai.user.service.AgentRequestService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentRequestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AgentRequestControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentRequestService agentRequestService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void unauthenticated_cannotAccessAnyEndpoint() throws Exception {
        mockMvc.perform(post("/api/agent-requests"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/agent-requests/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/agent-requests"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/agent-requests/1/approve"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/agent-requests/1/reject"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customer_canSubmitAndGetOwnRequest() throws Exception {
        AgentRequestResponse response = new AgentRequestResponse(
                1L, 10L, "Customer Name", "cust@support.ai",
                AgentRequestStatus.PENDING, "I want to help", null, null,
                null, null, LocalDateTime.now(), null
        );

        when(agentRequestService.submitRequest(eq(10L), any())).thenReturn(response);
        when(agentRequestService.getMyLatestRequest(10L)).thenReturn(response);

        mockMvc.perform(post("/api/agent-requests")
                        .with(jwt().jwt(j -> j.subject("10").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reason": "I want to help"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/agent-requests/me")
                        .with(jwt().jwt(j -> j.subject("10").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk());
    }

    @Test
    void customer_forbidden_fromAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/agent-requests")
                        .with(jwt().jwt(j -> j.subject("10").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/agent-requests/1/approve")
                        .with(jwt().jwt(j -> j.subject("10").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "adminNotes": "Notes"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/agent-requests/1/reject")
                        .with(jwt().jwt(j -> j.subject("10").claim("role", "CUSTOMER"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "rejectionReason": "Valid reason"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void agent_forbidden_fromSubmittingOrReviewing() throws Exception {
        mockMvc.perform(post("/api/agent-requests")
                        .with(jwt().jwt(j -> j.subject("20").claim("role", "AGENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AGENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reason": "Again"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/agent-requests/1/approve")
                        .with(jwt().jwt(j -> j.subject("20").claim("role", "AGENT"))
                                .authorities(new SimpleGrantedAuthority("ROLE_AGENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canAccessAllEndpoints() throws Exception {
        PageResponse<AgentRequestResponse> pageResponse = new PageResponse<>(
                List.of(), 0, 20, 0, 0, true, true
        );
        when(agentRequestService.getAgentRequests(0, 20, null)).thenReturn(pageResponse);

        AgentRequestResponse approvedResponse = new AgentRequestResponse(
                1L, 10L, "Customer Name", "cust@support.ai",
                AgentRequestStatus.APPROVED, "Reason", "Notes", null,
                1L, "Admin Name", LocalDateTime.now(), LocalDateTime.now()
        );
        when(agentRequestService.approveRequest(eq(1L), any(), eq(1L))).thenReturn(approvedResponse);

        AgentRequestResponse rejectedResponse = new AgentRequestResponse(
                1L, 10L, "Customer Name", "cust@support.ai",
                AgentRequestStatus.REJECTED, "Reason", null, "Valid reason text",
                1L, "Admin Name", LocalDateTime.now(), LocalDateTime.now()
        );
        when(agentRequestService.rejectRequest(eq(1L), any(), eq(1L))).thenReturn(rejectedResponse);

        mockMvc.perform(get("/api/agent-requests")
                        .with(jwt().jwt(j -> j.subject("1").claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/agent-requests/1/approve")
                        .with(jwt().jwt(j -> j.subject("1").claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "adminNotes": "Welcome"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/agent-requests/1/reject")
                        .with(jwt().jwt(j -> j.subject("1").claim("role", "ADMIN"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "rejectionReason": "Valid reason text"
                                }
                                """))
                .andExpect(status().isOk());
    }
}
