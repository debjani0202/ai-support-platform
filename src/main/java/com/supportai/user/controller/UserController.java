package com.supportai.user.controller;

import com.supportai.ticket.dto.PageResponse;
import com.supportai.user.dto.AdminCreateUserRequest;
import com.supportai.user.dto.AgentResponse;
import com.supportai.user.dto.UpdateUserRoleRequest;
import com.supportai.user.dto.UserProfileResponse;
import com.supportai.user.dto.UserSummaryResponse;
import com.supportai.user.entity.Role;
import com.supportai.user.service.UserService;
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

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/agents")
    public List<AgentResponse> getAgents() {
        return userService.getAgents();
    }

    @GetMapping("/me")
    public UserProfileResponse getCurrentUserProfile(Authentication authentication) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return userService.getUserProfile(currentUserId);
    }

    @GetMapping
    public PageResponse<UserSummaryResponse> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String search
    ) {
        return userService.getUsers(page, size, role, search);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummaryResponse createUser(
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        return userService.createUser(request);
    }

    @PatchMapping("/{id}/role")
    public UserSummaryResponse updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request,
            Authentication authentication
    ) {
        Long currentUserId = Long.valueOf(authentication.getName());
        return userService.updateUserRole(id, request, currentUserId);
    }
}
