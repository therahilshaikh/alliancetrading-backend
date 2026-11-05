package in.ruhmans.alliancetrading.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.auth.domain.OtpRequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OtpRequestRepository extends JpaRepository<OtpRequest, UUID> {

    @Query("SELECT o FROM OtpRequest o WHERE o.mobile = :mobile AND o.expiresAt > :now ORDER BY o.createdAt DESC")
    List<OtpRequest> findActiveByMobile(String mobile, Instant now);

    @Query("SELECT COUNT(o) FROM OtpRequest o WHERE o.mobile = :mobile AND o.createdAt > :since")
    long countRecentByMobile(String mobile, Instant since);
}
