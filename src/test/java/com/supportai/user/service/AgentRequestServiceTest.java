package com.supportai.user.service;

import com.supportai.exception.ForbiddenException;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.notification.service.NotificationService;
import com.supportai.user.dto.AgentRequestResponse;
import com.supportai.user.dto.ApproveAgentRequestDto;
import com.supportai.user.dto.CreateAgentRequestDto;
import com.supportai.user.dto.RejectAgentRequestDto;
import com.supportai.user.entity.AgentRequest;
import com.supportai.user.entity.AgentRequestStatus;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.AgentRequestRepository;
import com.supportai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentRequestServiceTest {

    @Mock
    private AgentRequestRepository agentRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AgentRequestService agentRequestService;

    private User customer;
    private User admin;
    private User agent;

    @BeforeEach
    void setUp() {
        customer = new User("Jane Customer", "customer@support.ai", "encodedPass", Role.CUSTOMER);
        ReflectionTestUtils.setField(customer, "id", 10L);

        admin = new User("Chief Admin", "admin@support.ai", "encodedPass", Role.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 1L);

        agent = new User("Existing Agent", "agent@support.ai", "encodedPass", Role.AGENT);
        ReflectionTestUtils.setField(agent, "id", 20L);
    }

    @Test
    void submitRequest_success_whenCustomer() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(agentRequestRepository.existsByUserIdAndStatus(10L, AgentRequestStatus.PENDING)).thenReturn(false);
        when(agentRequestRepository.save(any(AgentRequest.class))).thenAnswer(invocation -> {
            AgentRequest r = invocation.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 100L);
            return r;
        });

        CreateAgentRequestDto dto = new CreateAgentRequestDto("Experienced support agent");
        AgentRequestResponse response = agentRequestService.submitRequest(10L, dto);

        assertNotNull(response);
        assertEquals(AgentRequestStatus.PENDING, response.status());
        assertEquals("Experienced support agent", response.reason());
        assertEquals(10L, response.userId());
        assertNull(response.rejectionReason());
        verify(agentRequestRepository).save(any(AgentRequest.class));
    }

    @Test
    void submitRequest_throws_whenAlreadyPending() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(agentRequestRepository.existsByUserIdAndStatus(10L, AgentRequestStatus.PENDING)).thenReturn(true);

        CreateAgentRequestDto dto = new CreateAgentRequestDto("Another request");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agentRequestService.submitRequest(10L, dto));

        assertTrue(ex.getMessage().contains("already have an agent access request pending review"));
        verify(agentRequestRepository, never()).save(any());
    }

    @Test
    void submitRequest_throws_whenRoleIsNotCustomer() {
        when(userRepository.findById(20L)).thenReturn(Optional.of(agent));

        CreateAgentRequestDto dto = new CreateAgentRequestDto("I am already agent");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agentRequestService.submitRequest(20L, dto));

        assertTrue(ex.getMessage().contains("Only CUSTOMER accounts are eligible"));
        verify(agentRequestRepository, never()).save(any());
    }

    @Test
    void approveRequest_success_promotesCustomerToAgent() {
        AgentRequest request = new AgentRequest(customer, "Motivation text");
        ReflectionTestUtils.setField(request, "id", 101L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(agentRequestRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(request));
        when(userRepository.save(customer)).thenReturn(customer);

        ApproveAgentRequestDto dto = new ApproveAgentRequestDto("Approved by senior admin");
        AgentRequestResponse response = agentRequestService.approveRequest(101L, dto, 1L);

        assertEquals(AgentRequestStatus.APPROVED, response.status());
        assertEquals(Role.AGENT, customer.getRole());
        assertEquals("Approved by senior admin", response.adminNotes());
        assertEquals(1L, response.reviewedById());
        verify(userRepository).save(customer);
        verify(notificationService).createNotification(
                eq(customer),
                isNull(),
                eq("AGENT_REQUEST_APPROVED"),
                contains("approved")
        );
    }

    @Test
    void approveRequest_throws_whenApplicantIsAdmin() {
        User adminApplicant = new User("Other Admin", "other_admin@support.ai", "pass", Role.ADMIN);
        ReflectionTestUtils.setField(adminApplicant, "id", 2L);

        AgentRequest request = new AgentRequest(adminApplicant, "Motivation text");
        ReflectionTestUtils.setField(request, "id", 102L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(agentRequestRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(request));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agentRequestService.approveRequest(102L, new ApproveAgentRequestDto("Notes"), 1L));

        assertTrue(ex.getMessage().contains("ADMINISTRATOR and cannot be demoted or modified"));
        assertEquals(Role.ADMIN, adminApplicant.getRole()); // Unmodified
        verify(userRepository, never()).save(adminApplicant);
    }

    @Test
    void approveRequest_throws_whenApplicantAlreadyAgent() {
        AgentRequest request = new AgentRequest(agent, "Motivation text");
        ReflectionTestUtils.setField(request, "id", 103L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(agentRequestRepository.findByIdForUpdate(103L)).thenReturn(Optional.of(request));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agentRequestService.approveRequest(103L, new ApproveAgentRequestDto("Notes"), 1L));

        assertTrue(ex.getMessage().contains("already a SUPPORT AGENT"));
        verify(userRepository, never()).save(agent);
    }

    @Test
    void approveRequest_throws_whenReviewerIsNotAdmin() {
        when(userRepository.findById(20L)).thenReturn(Optional.of(agent));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> agentRequestService.approveRequest(101L, new ApproveAgentRequestDto("Notes"), 20L));

        assertTrue(ex.getMessage().contains("Only administrators"));
    }

    @Test
    void approveRequest_throws_whenSelfApproval() {
        User adminApplicant = new User("Self Admin", "self@support.ai", "pass", Role.CUSTOMER);
        ReflectionTestUtils.setField(adminApplicant, "id", 1L);

        AgentRequest request = new AgentRequest(adminApplicant, "I want to be agent");
        ReflectionTestUtils.setField(request, "id", 104L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(agentRequestRepository.findByIdForUpdate(104L)).thenReturn(Optional.of(request));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agentRequestService.approveRequest(104L, new ApproveAgentRequestDto("Notes"), 1L));

        assertTrue(ex.getMessage().contains("Administrators cannot review or approve their own requests"));
    }

    @Test
    void approveRequest_throws_whenRequestNotPending() {
        AgentRequest request = new AgentRequest(customer, "Motivation text");
        ReflectionTestUtils.setField(request, "id", 105L);
        request.setStatus(AgentRequestStatus.APPROVED);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(agentRequestRepository.findByIdForUpdate(105L)).thenReturn(Optional.of(request));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agentRequestService.approveRequest(105L, new ApproveAgentRequestDto("Notes"), 1L));

        assertTrue(ex.getMessage().contains("already APPROVED and cannot be reviewed again"));
    }

    @Test
    void rejectRequest_throws_whenReasonIsBlankOrTooShort() {
        AgentRequest request = new AgentRequest(customer, "Motivation text");
        ReflectionTestUtils.setField(request, "id", 106L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(agentRequestRepository.findByIdForUpdate(106L)).thenReturn(Optional.of(request));

        RejectAgentRequestDto shortDto = new RejectAgentRequestDto("No");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agentRequestService.rejectRequest(106L, shortDto, 1L));

        assertTrue(ex.getMessage().contains("Rejection reason is required"));
    }

    @Test
    void rejectRequest_success_preservesCustomerRoleAndStoresReason() {
        AgentRequest request = new AgentRequest(customer, "Motivation text");
        ReflectionTestUtils.setField(request, "id", 107L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(agentRequestRepository.findByIdForUpdate(107L)).thenReturn(Optional.of(request));

        RejectAgentRequestDto dto = new RejectAgentRequestDto("Insufficient ticket handling experience");
        AgentRequestResponse response = agentRequestService.rejectRequest(107L, dto, 1L);

        assertEquals(AgentRequestStatus.REJECTED, response.status());
        assertEquals("Insufficient ticket handling experience", response.rejectionReason());
        assertEquals(Role.CUSTOMER, customer.getRole()); // Still customer
        verify(userRepository, never()).save(customer);
        verify(notificationService).createNotification(
                eq(customer),
                isNull(),
                eq("AGENT_REQUEST_REJECTED"),
                contains("Insufficient ticket handling experience")
        );
    }

    @Test
    void submitRequest_throws_whenDataIntegrityViolationOccurs() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(agentRequestRepository.existsByUserIdAndStatus(10L, AgentRequestStatus.PENDING)).thenReturn(false);
        when(agentRequestRepository.save(any(AgentRequest.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Unique constraint violation uk_agent_requests_pending_per_user"));

        CreateAgentRequestDto dto = new CreateAgentRequestDto("Experienced support agent");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agentRequestService.submitRequest(10L, dto));

        assertTrue(ex.getMessage().contains("already have an agent access request pending review"));
    }

    @Test
    void reapply_success_whenPreviousRequestRejected() {
        // Customer with a previous REJECTED request
        AgentRequest rejectedRequest = new AgentRequest(customer, "First try");
        ReflectionTestUtils.setField(rejectedRequest, "id", 100L);
        rejectedRequest.setStatus(AgentRequestStatus.REJECTED);
        rejectedRequest.setRejectionReason("Needed more experience");

        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        // No PENDING request currently exists
        when(agentRequestRepository.existsByUserIdAndStatus(10L, AgentRequestStatus.PENDING)).thenReturn(false);
        when(agentRequestRepository.save(any(AgentRequest.class))).thenAnswer(invocation -> {
            AgentRequest r = invocation.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 108L);
            return r;
        });

        CreateAgentRequestDto dto = new CreateAgentRequestDto("Second try with more details");
        AgentRequestResponse response = agentRequestService.submitRequest(10L, dto);

        assertNotNull(response);
        assertEquals(108L, response.id());
        assertEquals(AgentRequestStatus.PENDING, response.status());
        assertEquals("Second try with more details", response.reason());
        assertNull(response.rejectionReason());
        verify(agentRequestRepository).save(any(AgentRequest.class));
    }
}
