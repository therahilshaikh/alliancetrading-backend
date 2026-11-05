package in.ruhmans.alliancetrading.kyc.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.kyc.domain.KycProfile;
import in.ruhmans.alliancetrading.user.domain.UserStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface KycProfileRepository extends JpaRepository<KycProfile, UUID> {

    Optional<KycProfile> findByUserId(UUID userId);

    Optional<KycProfile> findByPanHash(String panHash);

    Optional<KycProfile> findByAadhaarHash(String aadhaarHash);

    List<KycProfile> findByUserIdIn(Collection<UUID> userIds);

    /** Oldest submissions first, so the queue is worked in arrival order. */
    @Query(value = "SELECT new in.ruhmans.alliancetrading.kyc.repository.KycQueueRow("
            + "u.id, u.mobile, u.status, k.fullName, k.dob, k.panLast4, k.aadhaarLast4, "
            + "k.updatedAt, k.reviewedAt, k.rejectionReason) "
            + "FROM KycProfile k JOIN User u ON u.id = k.userId "
            + "WHERE u.status = :status ORDER BY k.updatedAt ASC",
            countQuery = "SELECT COUNT(k) FROM KycProfile k JOIN User u ON u.id = k.userId "
                    + "WHERE u.status = :status")
    Page<KycQueueRow> findQueue(UserStatus status, Pageable pageable);
}
