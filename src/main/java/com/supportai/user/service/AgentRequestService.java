package com.supportai.user.service;

import com.supportai.exception.ForbiddenException;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.notification.service.NotificationService;
import com.supportai.ticket.dto.PageResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class AgentRequestService {

    private final AgentRequestRepository agentRequestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public AgentRequestService(
            AgentRequestRepository agentRequestRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.agentRequestRepository = agentRequestRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public AgentRequestResponse submitRequest(Long currentUserId, CreateAgentRequestDto dto) {
        User applicant = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        if (applicant.getRole() != Role.CUSTOMER) {
            throw new IllegalArgumentException(
                    "Only CUSTOMER accounts are eligible to submit an agent access request. Current role: " + applicant.getRole()
            );
        }

        if (agentRequestRepository.existsByUserIdAndStatus(currentUserId, AgentRequestStatus.PENDING)) {
            throw new IllegalArgumentException("You already have an agent access request pending review");
        }

        String reason = (dto != null && dto.reason() != null && !dto.reason().trim().isBlank())
                ? dto.reason().trim()
                : null;

        AgentRequest request = new AgentRequest(applicant, reason);

        try {
            AgentRequest saved = agentRequestRepository.save(request);
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("You already have an agent access request pending review", ex);
        }
    }

    @Transactional(readOnly = true)
    public AgentRequestResponse getMyLatestRequest(Long currentUserId) {
        return agentRequestRepository.findTopByUserIdOrderByCreatedAtDesc(currentUserId)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PageResponse<AgentRequestResponse> getAgentRequests(int page, int size, AgentRequestStatus status) {
        if (page < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AgentRequest> resultPage = (status != null)
                ? agentRequestRepository.findByStatus(status, pageable)
                : agentRequestRepository.findAll(pageable);

        return new PageResponse<>(
                resultPage.getContent().stream().map(this::toResponse).toList(),
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                resultPage.isFirst(),
                resultPage.isLast()
        );
    }

    public AgentRequestResponse approveRequest(Long requestId, ApproveAgentRequestDto dto, Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer not found with id: " + adminId));

        if (admin.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only administrators are authorized to approve agent access requests");
        }

        AgentRequest request = agentRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent request not found with id: " + requestId));

        if (request.getStatus() != AgentRequestStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Cannot review request: request is already " + request.getStatus() + " and cannot be reviewed again"
            );
        }

        User applicant = request.getUser();

        if (applicant.getId().equals(adminId)) {
            throw new IllegalArgumentException("Administrators cannot review or approve their own requests");
        }

        if (applicant.getRole() != Role.CUSTOMER) {
            if (applicant.getRole() == Role.ADMIN) {
                throw new IllegalArgumentException(
                        "Cannot promote user: applicant is an ADMINISTRATOR and cannot be demoted or modified"
                );
            }
            if (applicant.getRole() == Role.AGENT) {
                throw new IllegalArgumentException("Cannot promote user: applicant is already a SUPPORT AGENT");
            }
            throw new IllegalArgumentException(
                    "Cannot promote user: only accounts with CUSTOMER role are eligible for promotion to AGENT"
            );
        }

        request.setStatus(AgentRequestStatus.APPROVED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        if (dto != null && dto.adminNotes() != null && !dto.adminNotes().trim().isBlank()) {
            request.setAdminNotes(dto.adminNotes().trim());
        }

        applicant.setRole(Role.AGENT);
        userRepository.save(applicant);

        notificationService.createNotification(
                applicant,
                null,
                "AGENT_REQUEST_APPROVED",
                "Your request to become a Support Agent has been approved! Activate your Agent workspace from your profile."
        );

        return toResponse(request);
    }

    public AgentRequestResponse rejectRequest(Long requestId, RejectAgentRequestDto dto, Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Reviewer not found with id: " + adminId));

        if (admin.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only administrators are authorized to reject agent access requests");
        }

        AgentRequest request = agentRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent request not found with id: " + requestId));

        if (request.getStatus() != AgentRequestStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Cannot review request: request is already " + request.getStatus() + " and cannot be reviewed again"
            );
        }

        if (dto == null || dto.rejectionReason() == null || dto.rejectionReason().trim().length() < 5) {
            throw new IllegalArgumentException("Rejection reason is required and must be between 5 and 1000 characters");
        }

        String rejectionReason = dto.rejectionReason().trim();

        request.setStatus(AgentRequestStatus.REJECTED);
        request.setReviewedBy(admin);
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectionReason(rejectionReason);

        User applicant = request.getUser();

        notificationService.createNotification(
                applicant,
                null,
                "AGENT_REQUEST_REJECTED",
                "Your request to become a Support Agent was reviewed. Reason: " + rejectionReason
        );

        return toResponse(request);
    }

    private AgentRequestResponse toResponse(AgentRequest request) {
        return new AgentRequestResponse(
                request.getId(),
                request.getUser().getId(),
                request.getUser().getName(),
                request.getUser().getEmail(),
                request.getStatus(),
                request.getReason(),
                request.getAdminNotes(),
                request.getRejectionReason(),
                request.getReviewedBy() != null ? request.getReviewedBy().getId() : null,
                request.getReviewedBy() != null ? request.getReviewedBy().getName() : null,
                request.getCreatedAt(),
                request.getReviewedAt()
        );
    }
}
