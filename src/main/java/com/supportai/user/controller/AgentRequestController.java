package com.supportai.user.controller;

import com.supportai.ticket.dto.PageResponse;
import com.supportai.user.dto.AgentRequestResponse;
import com.supportai.user.dto.ApproveAgentRequestDto;
import com.supportai.user.dto.CreateAgentRequestDto;
import com.supportai.user.dto.RejectAgentRequestDto;
import com.supportai.user.entity.AgentRequestStatus;
import com.supportai.user.service.AgentRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-requests")
public class AgentRequestController {

    private final AgentRequestService agentRequestService;

    public AgentRequestController(AgentRequestService agentRequestService) {
        this.agentRequestService = agentRequestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentRequestResponse submitRequest(
            @Valid @RequestBody(required = false) CreateAgentRequestDto dto,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return agentRequestService.submitRequest(currentUserId, dto);
    }

    @GetMapping("/me")
    public AgentRequestResponse getMyLatestRequest(Authentication authentication) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return agentRequestService.getMyLatestRequest(currentUserId);
    }

    @GetMapping
    public PageResponse<AgentRequestResponse> getAgentRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) AgentRequestStatus status
    ) {
        return agentRequestService.getAgentRequests(page, size, status);
    }

    @PatchMapping("/{id}/approve")
    public AgentRequestResponse approveRequest(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ApproveAgentRequestDto dto,
            Authentication authentication
    ) {
        Long adminId = Long.valueOf(authentication.getName());
        return agentRequestService.approveRequest(id, dto, adminId);
    }

    @PatchMapping("/{id}/reject")
    public AgentRequestResponse rejectRequest(
            @PathVariable Long id,
            @Valid @RequestBody RejectAgentRequestDto dto,
            Authentication authentication
    ) {
        Long adminId = Long.valueOf(authentication.getName());
        return agentRequestService.rejectRequest(id, dto, adminId);
    }
}
