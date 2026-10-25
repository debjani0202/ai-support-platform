package com.supportai.user.service;

import com.supportai.user.dto.AgentResponse;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Retrieves all users with the AGENT role, sorted alphabetically by name.
     *
     * @return a list of AgentResponse DTOs, or an empty list if no agents exist
     */
    public List<AgentResponse> getAgents() {
        return userRepository.findByRoleOrderByNameAsc(Role.AGENT)
                .stream()
                .map(this::toAgentResponse)
                .toList();
    }

    private AgentResponse toAgentResponse(User user) {
        return new AgentResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
