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
import com.supportai.user.repository.UserSpecifications;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    /*
     * IMPORTANT:
     * Verify this name against your database migration.
     * Replace it with the actual unique constraint name for email.
     */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";

    @Autowired
    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EntityManager entityManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    /*
     * Compatibility constructor for tests that instantiate the service
     * without an EntityManager.
     */
    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this(userRepository, passwordEncoder, null);
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

    /**
     * Retrieves the safe profile of the user with the given ID.
     *
     * @param userId ID of the user
     * @return safe user profile
     */
    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + userId
                        )
                );

        return toUserProfileResponse(user);
    }

    /**
     * Retrieves a paginated list of users with optional role filtering
     * and text search.
     *
     * @param page   zero-based page number
     * @param size   page size, between 1 and 100
     * @param role   optional role filter
     * @param search optional search text
     * @return paginated user summaries
     */
    public PageResponse<UserSummaryResponse> getUsers(
            int page,
            int size,
            Role role,
            String search
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100"
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "name")
        );

        Specification<User> spec =
                Specification.where(UserSpecifications.hasRole(role))
                        .and(UserSpecifications.matchesSearch(search));

        Page<User> userPage = userRepository.findAll(spec, pageable);

        return new PageResponse<>(
                userPage.getContent()
                        .stream()
                        .map(this::toUserSummaryResponse)
                        .toList(),
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.isFirst(),
                userPage.isLast()
        );
    }

    /**
     * Creates a user with an encoded password.
     * Intended for use by the admin-only endpoint.
     *
     * @param request validated user creation request
     * @return safe summary of the created user
     */
    @Transactional
    public UserSummaryResponse createUser(AdminCreateUserRequest request) {
        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "Email is already registered"
            );
        }

        String encodedPassword =
                passwordEncoder.encode(request.password());

        User user = new User(
                request.name().trim(),
                email,
                encodedPassword,
                request.role()
        );

        try {
            User savedUser = userRepository.save(user);
            return toUserSummaryResponse(savedUser);
        } catch (DataIntegrityViolationException ex) {
            if (isEmailUniqueConstraintViolation(ex)) {
                throw new IllegalArgumentException(
                        "Email is already registered",
                        ex
                );
            }

            // Preserve unrelated database integrity errors.
            throw ex;
        }
    }

    /**
     * PostgreSQL advisory-lock key used to serialize administrator
     * role mutations.
     */
    public static final long ADMIN_ROLE_MUTATION_LOCK_KEY =
            84920491823L;

    /**
     * Updates a user's role while preventing self-demotion and protecting
     * the last remaining administrator.
     *
     * @param targetUserId ID of the user whose role will change
     * @param request      requested role
     * @param currentUserId ID of the authenticated user
     * @return safe summary of the updated user
     */
    @Transactional
    public UserSummaryResponse updateUserRole(
            Long targetUserId,
            UpdateUserRoleRequest request,
            Long currentUserId
    ) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + targetUserId
                        )
                );

        Role newRole = request.role();

        // Prevent an administrator from demoting their own account.
        if (targetUser.getId().equals(currentUserId)
                && targetUser.getRole() == Role.ADMIN
                && newRole != Role.ADMIN) {
            throw new IllegalArgumentException(
                    "Administrators cannot demote their own account"
            );
        }

        // Protect the last remaining administrator.
        if (targetUser.getRole() == Role.ADMIN
                && newRole != Role.ADMIN) {

            /*
             * Acquire a transaction-scoped PostgreSQL advisory lock.
             * This serializes concurrent administrator demotions.
             */
            userRepository.acquireAdminRoleLock(
                    ADMIN_ROLE_MUTATION_LOCK_KEY
            );

            /*
             * Refresh the target after acquiring the lock so its role
             * reflects the latest committed database state.
             */
            if (entityManager != null) {
                entityManager.refresh(targetUser);
            }

            if (targetUser.getRole() == Role.ADMIN
                    && newRole != Role.ADMIN) {

                long adminCount =
                        userRepository.countByRole(Role.ADMIN);

                if (adminCount <= 1) {
                    throw new IllegalArgumentException(
                            "Cannot demote the last remaining administrator"
                    );
                }
            }
        }

        if (targetUser.getRole() != newRole) {
            targetUser.setRole(newRole);

            User updatedUser = userRepository.save(targetUser);

            return toUserSummaryResponse(updatedUser);
        }

        return toUserSummaryResponse(targetUser);
    }

    /**
     * Returns true only when the database reports the configured
     * email uniqueness constraint as the violated constraint.
     */
    private boolean isEmailUniqueConstraintViolation(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();

                if (constraintName != null
                        && EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(
                                constraintName
                        )) {
                    return true;
                }
            }

            String message = cause.getMessage();
            if (message != null
                    && message.toLowerCase(Locale.ROOT)
                            .contains(EMAIL_UNIQUE_CONSTRAINT)) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    private AgentResponse toAgentResponse(User user) {
        return new AgentResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    private UserProfileResponse toUserProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    private UserSummaryResponse toUserSummaryResponse(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
