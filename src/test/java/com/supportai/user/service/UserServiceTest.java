package com.supportai.user.service;

import com.supportai.user.dto.AgentResponse;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserService(userRepository);
    }

    @Test
    void getAgents_retrievesAndMapsAllFieldsCorrectly() {
        User agent = mock(User.class);
        when(agent.getId()).thenReturn(10L);
        when(agent.getName()).thenReturn("Alice Smith");
        when(agent.getEmail()).thenReturn("alice@example.com");
        when(agent.getRole()).thenReturn(Role.AGENT);
        when(agent.getPassword()).thenReturn("encoded_hash_never_exposed");

        when(userRepository.findByRoleOrderByNameAsc(Role.AGENT))
                .thenReturn(List.of(agent));

        List<AgentResponse> result = userService.getAgents();

        assertNotNull(result);
        assertEquals(1, result.size());
        AgentResponse response = result.get(0);
        assertEquals(10L, response.id());
        assertEquals("Alice Smith", response.name());
        assertEquals("alice@example.com", response.email());
        assertEquals(Role.AGENT, response.role());
    }

    @Test
    void getAgents_preservesAlphabeticalOrderFromRepository() {
        User agentA = mock(User.class);
        when(agentA.getId()).thenReturn(1L);
        when(agentA.getName()).thenReturn("Alice Agent");
        when(agentA.getEmail()).thenReturn("alice@example.com");
        when(agentA.getRole()).thenReturn(Role.AGENT);

        User agentB = mock(User.class);
        when(agentB.getId()).thenReturn(2L);
        when(agentB.getName()).thenReturn("Bob Agent");
        when(agentB.getEmail()).thenReturn("bob@example.com");
        when(agentB.getRole()).thenReturn(Role.AGENT);

        when(userRepository.findByRoleOrderByNameAsc(Role.AGENT))
                .thenReturn(List.of(agentA, agentB));

        List<AgentResponse> result = userService.getAgents();

        assertEquals(2, result.size());
        assertEquals("Alice Agent", result.get(0).name());
        assertEquals("Bob Agent", result.get(1).name());
    }

    @Test
    void getAgents_queriesOnlyAgentRoleFromRepository() {
        when(userRepository.findByRoleOrderByNameAsc(Role.AGENT))
                .thenReturn(List.of());

        userService.getAgents();

        verify(userRepository, times(1)).findByRoleOrderByNameAsc(Role.AGENT);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void getAgents_neverExposesPasswordOrPasswordHash() {
        User agent = mock(User.class);
        when(agent.getId()).thenReturn(5L);
        when(agent.getName()).thenReturn("Agent Agent");
        when(agent.getEmail()).thenReturn("agent@example.com");
        when(agent.getRole()).thenReturn(Role.AGENT);
        when(agent.getPassword()).thenReturn("sensitive_bcrypt_hash_$2a$10$xyz");

        when(userRepository.findByRoleOrderByNameAsc(Role.AGENT))
                .thenReturn(List.of(agent));

        List<AgentResponse> result = userService.getAgents();

        assertEquals(1, result.size());
        AgentResponse response = result.get(0);
        assertEquals(5L, response.id());
        verify(agent, never()).getPassword();
    }

    @Test
    void getAgents_whenNoAgentsExist_returnsEmptyList() {
        when(userRepository.findByRoleOrderByNameAsc(Role.AGENT))
                .thenReturn(List.of());

        List<AgentResponse> result = userService.getAgents();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
