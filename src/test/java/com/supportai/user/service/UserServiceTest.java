package com.supportai.user.service;

import com.supportai.exception.ResourceNotFoundException;
import com.supportai.ticket.dto.PageResponse;
import com.supportai.user.dto.AdminCreateUserRequest;
import com.supportai.user.dto.AgentResponse;
import com.supportai.user.dto.UpdateUserRoleRequest;
import com.supportai.user.dto.UserProfileResponse;
import com.supportai.user.dto.UserSummaryResponse;
import com.supportai.user.entity.Role;
import com.supportai.user.entity.User;
import com.supportai.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private EntityManager entityManager;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        entityManager = mock(EntityManager.class);
        userService = new UserService(userRepository, passwordEncoder, entityManager);
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

    @Test
    void getUserProfile_returnsSafeProfileForExistingUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getName()).thenReturn("Jane Doe");
        when(user.getEmail()).thenReturn("jane@example.com");
        when(user.getRole()).thenReturn(Role.CUSTOMER);
        when(user.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 1, 1, 12, 0));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserProfileResponse profile = userService.getUserProfile(1L);

        assertNotNull(profile);
        assertEquals(1L, profile.id());
        assertEquals("Jane Doe", profile.name());
        assertEquals("jane@example.com", profile.email());
        assertEquals(Role.CUSTOMER, profile.role());
        assertEquals(LocalDateTime.of(2026, 1, 1, 12, 0), profile.createdAt());
        verify(user, never()).getPassword();
    }

    @Test
    void getUserProfile_throwsResourceNotFoundWhenUserDoesNotExist() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserProfile(999L));
    }

    @Test
    void getUsers_validatesPagingParameters() {
        assertThrows(IllegalArgumentException.class, () -> userService.getUsers(-1, 20, null, null));
        assertThrows(IllegalArgumentException.class, () -> userService.getUsers(0, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> userService.getUsers(0, 101, null, null));
    }

    @Test
    void getUsers_returnsPagedUserSummaryResponse() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getName()).thenReturn("John Admin");
        when(user.getEmail()).thenReturn("admin@example.com");
        when(user.getRole()).thenReturn(Role.ADMIN);
        when(user.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 1, 1, 10, 0));
        when(user.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 1, 1, 10, 0));

        Page<User> pagedUsers = new PageImpl<>(List.of(user));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(pagedUsers);

        PageResponse<UserSummaryResponse> response = userService.getUsers(0, 20, Role.ADMIN, "john");

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("John Admin", response.content().get(0).name());
        assertEquals(Role.ADMIN, response.content().get(0).role());
    }

    @Test
    void createUser_successfullyCreatesUserWithHashedPassword() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "New Agent",
                "agent@example.com",
                "Password123!",
                Role.AGENT
        );

        when(userRepository.existsByEmail("agent@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded_pass");

        User savedUser = mock(User.class);
        when(savedUser.getId()).thenReturn(20L);
        when(savedUser.getName()).thenReturn("New Agent");
        when(savedUser.getEmail()).thenReturn("agent@example.com");
        when(savedUser.getRole()).thenReturn(Role.AGENT);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserSummaryResponse response = userService.createUser(request);

        assertNotNull(response);
        assertEquals(20L, response.id());
        assertEquals("New Agent", response.name());
        assertEquals(Role.AGENT, response.role());
        verify(passwordEncoder).encode("Password123!");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_rejectsDuplicateEmail() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "Duplicate",
                "dup@example.com",
                "Password123!",
                Role.CUSTOMER
        );

        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.createUser(request)
        );
        assertEquals("Email is already registered", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_handlesDataIntegrityViolationSafely() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "Race User",
                "race@example.com",
                "Password123!",
                Role.CUSTOMER
        );

        when(userRepository.existsByEmail("race@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        ConstraintViolationException cve = new ConstraintViolationException(
                "duplicate key value violates unique constraint \"uk_users_email\"",
                new SQLException("duplicate key", "23505"),
                "uk_users_email"
        );
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint", cve));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.createUser(request)
        );
        assertEquals("Email is already registered", ex.getMessage());
    }

    @Test
    void createUser_rethrowsUnrelatedDataIntegrityViolation() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "Race User",
                "race@example.com",
                "Password123!",
                Role.CUSTOMER
        );

        when(userRepository.existsByEmail("race@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        ConstraintViolationException cve = new ConstraintViolationException(
                "check constraint \"chk_users_role\" violated",
                new SQLException("check constraint", "23514"),
                "chk_users_role"
        );
        DataIntegrityViolationException dive =
                new DataIntegrityViolationException("Check constraint violation", cve);

        when(userRepository.save(any(User.class))).thenThrow(dive);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> userService.createUser(request)
        );
        assertSame(dive, thrown);
    }

    @Test
    void createUser_rethrowsBareDataIntegrityViolationWithoutConstraint() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "Race User",
                "race@example.com",
                "Password123!",
                Role.CUSTOMER
        );

        when(userRepository.existsByEmail("race@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        DataIntegrityViolationException dive =
                new DataIntegrityViolationException("Unspecified data integrity violation");

        when(userRepository.save(any(User.class))).thenThrow(dive);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> userService.createUser(request)
        );
        assertSame(dive, thrown);
    }

    @Test
    void updateUserRole_rejectsSelfDemotion() {
        User adminUser = mock(User.class);
        when(adminUser.getId()).thenReturn(1L);
        when(adminUser.getRole()).thenReturn(Role.ADMIN);

        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        UpdateUserRoleRequest request = new UpdateUserRoleRequest(Role.CUSTOMER);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.updateUserRole(1L, request, 1L)
        );

        assertTrue(ex.getMessage().contains("cannot demote their own account"));
        verify(adminUser, never()).setRole(any());
    }

    @Test
    void updateUserRole_rejectsDemotingLastRemainingAdmin() {
        User targetAdmin = mock(User.class);
        when(targetAdmin.getId()).thenReturn(2L);
        when(targetAdmin.getRole()).thenReturn(Role.ADMIN);

        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        UpdateUserRoleRequest request = new UpdateUserRoleRequest(Role.AGENT);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.updateUserRole(2L, request, 1L)
        );

        assertTrue(ex.getMessage().contains("last remaining administrator"));
        verify(userRepository).acquireAdminRoleLock(UserService.ADMIN_ROLE_MUTATION_LOCK_KEY);
        verify(entityManager).refresh(targetAdmin);
        verify(targetAdmin, never()).setRole(any());
    }

    @Test
    void updateUserRole_succeedsWhenMultipleAdminsExist() {
        User targetAdmin = mock(User.class);
        when(targetAdmin.getId()).thenReturn(2L);
        when(targetAdmin.getName()).thenReturn("Second Admin");
        when(targetAdmin.getEmail()).thenReturn("admin2@example.com");
        when(targetAdmin.getRole()).thenReturn(Role.ADMIN);

        when(userRepository.findById(2L)).thenReturn(Optional.of(targetAdmin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L);
        when(userRepository.save(targetAdmin)).thenReturn(targetAdmin);

        UpdateUserRoleRequest request = new UpdateUserRoleRequest(Role.AGENT);

        UserSummaryResponse response = userService.updateUserRole(2L, request, 1L);

        assertNotNull(response);
        verify(userRepository).acquireAdminRoleLock(UserService.ADMIN_ROLE_MUTATION_LOCK_KEY);
        verify(entityManager).refresh(targetAdmin);
        verify(targetAdmin).setRole(Role.AGENT);
        verify(userRepository).save(targetAdmin);
    }

    @Test
    void updateUserRole_refreshesTargetUserAfterAcquiringAdvisoryLock_whenTargetUserAlreadyLoadedBeforeWaitingForLock() {
        User targetUser = mock(User.class);
        when(targetUser.getId()).thenReturn(2L);
        when(targetUser.getName()).thenReturn("Target Admin");
        when(targetUser.getEmail()).thenReturn("target@supportai.com");
        when(targetUser.getCreatedAt()).thenReturn(LocalDateTime.now());
        when(targetUser.getUpdatedAt()).thenReturn(LocalDateTime.now());
        when(targetUser.getRole()).thenReturn(Role.ADMIN);

        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));

        // Simulate concurrent transaction demoting the user while this transaction was waiting for the advisory lock:
        // when entityManager.refresh is called, targetUser's role is refreshed to CUSTOMER
        doAnswer(invocation -> {
            when(targetUser.getRole()).thenReturn(Role.CUSTOMER);
            return null;
        }).when(entityManager).refresh(targetUser);

        UpdateUserRoleRequest request = new UpdateUserRoleRequest(Role.CUSTOMER);
        UserSummaryResponse response = userService.updateUserRole(2L, request, 1L);

        assertNotNull(response);
        verify(userRepository).acquireAdminRoleLock(UserService.ADMIN_ROLE_MUTATION_LOCK_KEY);
        verify(entityManager).refresh(targetUser);
        // Because targetUser is no longer ADMIN after refresh, countByRole should not be called
        verify(userRepository, never()).countByRole(any());
        assertEquals(Role.CUSTOMER, response.role());
    }
}
