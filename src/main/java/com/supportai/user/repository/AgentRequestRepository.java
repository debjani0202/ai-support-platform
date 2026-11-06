package com.supportai.user.repository;

import com.supportai.user.entity.AgentRequest;
import com.supportai.user.entity.AgentRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentRequestRepository extends JpaRepository<AgentRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AgentRequest r WHERE r.id = :id")
    Optional<AgentRequest> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUserIdAndStatus(Long userId, AgentRequestStatus status);

    Optional<AgentRequest> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    Page<AgentRequest> findByStatus(AgentRequestStatus status, Pageable pageable);
}
