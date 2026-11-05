package in.ruhmans.alliancetrading.admin.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.admin.domain.AdminUser;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByEmail(String email);

    List<AdminUser> findByStatusOrderByEmailAsc(String status);

    List<AdminUser> findAllByOrderByEmailAsc();
}
