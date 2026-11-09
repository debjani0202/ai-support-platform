package com.supportai.auth.repository;

import com.supportai.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    long USER_LOCK_PREFIX = 0x5553455200000000L;
    long FAMILY_LOCK_PREFIX = 0x46414D4900000000L;

    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(:lockKey)", nativeQuery = true)
    Integer acquireAdvisoryLock(@Param("lockKey") long lockKey);

    default void lockUserTokens(Long userId) {
        long key = USER_LOCK_PREFIX | (userId & 0xFFFFFFFFL);
        acquireAdvisoryLock(key);
    }

    default void lockTokenFamily(String family) {
        long hash;
        try {
            UUID uuid = UUID.fromString(family);
            hash = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        } catch (IllegalArgumentException e) {
            hash = family.hashCode();
        }
        long key = FAMILY_LOCK_PREFIX | (hash & 0x00000000FFFFFFFFL);
        acquireAdvisoryLock(key);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r WHERE r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashWithLock(@Param("tokenHash") String tokenHash);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("SELECT COUNT(r) > 0 FROM RefreshToken r WHERE r.tokenFamily = :family AND r.revokedAt IS NULL AND r.expiresAt > :now")
    boolean hasActiveTokenInFamily(@Param("family") String family, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.tokenFamily = :family AND r.revokedAt IS NULL")
    int revokeFamily(@Param("family") String family, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    Optional<RefreshToken> findFirstByTokenFamilyOrderByCreatedAtDesc(String family);
}

