package com.supportai.auth.repository;

import com.supportai.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PasswordResetToken p SET p.usedAt = :now WHERE p.user.id = :userId AND p.usedAt IS NULL")
    void invalidateActiveTokensForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
