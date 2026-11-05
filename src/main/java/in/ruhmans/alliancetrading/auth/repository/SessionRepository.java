package in.ruhmans.alliancetrading.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.auth.domain.Session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    @Query("SELECT s FROM Session s WHERE s.id = :id AND s.revokedAt IS NULL")
    Optional<Session> findActiveById(UUID id);

    @Query("SELECT s FROM Session s WHERE s.refreshTokenHash = :hash AND s.revokedAt IS NULL")
    Optional<Session> findActiveByRefreshTokenHash(String hash);

    @Query("SELECT s FROM Session s WHERE s.prevRefreshTokenHash = :hash AND s.revokedAt IS NULL")
    Optional<Session> findActiveByPrevRefreshTokenHash(String hash);

    @Modifying
    @Query("UPDATE Session s SET s.revokedAt = :now, s.revokeReason = :reason " +
           "WHERE s.userId = :userId AND s.revokedAt IS NULL")
    int revokeActiveUserSessions(UUID userId, Instant now, String reason);

    @Modifying
    @Query("UPDATE Session s SET s.revokedAt = :now, s.revokeReason = :reason " +
           "WHERE s.adminUserId = :adminUserId AND s.revokedAt IS NULL")
    int revokeActiveAdminSessions(UUID adminUserId, Instant now, String reason);
}
