package in.ruhmans.alliancetrading.user.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByMobile(String mobile);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(UUID id);

    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status")
    long countByStatus(UserStatus status);

    @Query("SELECT u.status, COUNT(u) FROM User u GROUP BY u.status")
    List<Object[]> countByStatusGrouped();

    @Query("SELECT MIN(u.createdAt) FROM User u WHERE u.status = :status")
    Instant oldestCreatedAtByStatus(UserStatus status);

    /**
     * Member search for admins. {@code pattern} is a lower-case LIKE pattern matched against
     * name and mobile ({@code "%"} for all). One live investment per user is guaranteed by
     * a partial unique index, so the investment join never duplicates rows.
     */
    @Query(value = "SELECT new in.ruhmans.alliancetrading.user.repository.AdminUserRow("
            + "u.id, k.fullName, u.mobile, u.status, u.createdAt, b.principal, b.profit, b.held, i.id, i.status) "
            + "FROM User u "
            + "LEFT JOIN KycProfile k ON k.userId = u.id "
            + "LEFT JOIN UserBalance b ON b.userId = u.id "
            + "LEFT JOIN Investment i ON i.userId = u.id "
            + "  AND i.status IN ('PENDING_VERIFICATION','ACTIVE','EXIT_REQUESTED') "
            + "WHERE u.status IN :statuses "
            + "AND (lower(coalesce(k.fullName, '')) LIKE :pattern OR u.mobile LIKE :pattern) "
            + "ORDER BY u.createdAt DESC",
            countQuery = "SELECT COUNT(u) FROM User u LEFT JOIN KycProfile k ON k.userId = u.id "
                    + "WHERE u.status IN :statuses "
                    + "AND (lower(coalesce(k.fullName, '')) LIKE :pattern OR u.mobile LIKE :pattern)")
    Page<AdminUserRow> searchForAdmin(Collection<UserStatus> statuses, String pattern, Pageable pageable);
}
