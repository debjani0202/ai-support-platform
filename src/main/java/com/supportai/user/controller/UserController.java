package com.supportai.user.controller;

import com.supportai.user.dto.AgentResponse;
import com.supportai.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
