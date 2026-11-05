package in.ruhmans.alliancetrading.admin.api;

import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.admin.domain.AdminRole;
import in.ruhmans.alliancetrading.admin.repository.AdminUserRepository;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every admin account, including the caller and disabled admins, so the console can turn any
 * admin ID (maker, approver, signed-by, audit actor) into an email. Readable by ADMIN and SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/admin-directory")
public class AdminDirectoryController {

    private final AdminUserRepository adminUserRepository;

    public AdminDirectoryController(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    public record AdminDirectoryEntry(UUID id, String email, String displayName, AdminRole role, String status) {}

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<AdminDirectoryEntry>> list() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(adminUserRepository.findAllByOrderByEmailAsc().stream()
                        .map(a -> new AdminDirectoryEntry(a.getId(), a.getEmail(), a.getDisplayName(), a.getRole(), a.getStatus()))
                        .toList());
    }
}
