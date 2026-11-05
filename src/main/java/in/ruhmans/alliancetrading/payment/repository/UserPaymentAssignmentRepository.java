package in.ruhmans.alliancetrading.payment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.payment.domain.UserPaymentAssignment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPaymentAssignmentRepository extends JpaRepository<UserPaymentAssignment, UUID> {

    Optional<UserPaymentAssignment> findByUserIdAndActiveTrue(UUID userId);

    List<UserPaymentAssignment> findByUserIdInAndActiveTrue(Collection<UUID> userIds);

    List<UserPaymentAssignment> findByUserIdOrderByAssignedAtDesc(UUID userId);
}
