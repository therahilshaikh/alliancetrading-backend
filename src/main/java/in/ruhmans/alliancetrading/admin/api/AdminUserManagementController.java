package in.ruhmans.alliancetrading.admin.api;

import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.admin.domain.AdminRole;
import in.ruhmans.alliancetrading.admin.domain.AdminUser;
import in.ruhmans.alliancetrading.admin.repository.AdminUserRepository;
import in.ruhmans.alliancetrading.auth.service.AdminAuthService;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/admin-users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminUserManagementController {

    private final AdminUserRepository adminUserRepository;
    private final AdminAuthService adminAuthService;

    public AdminUserManagementController(AdminUserRepository adminUserRepository,
                                         AdminAuthService adminAuthService) {
        this.adminUserRepository = adminUserRepository;
        this.adminAuthService = adminAuthService;
    }

    public record CreateAdminRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 100) String displayName,
            // Password is now the only admin factor, so require a reasonable length
            @NotBlank @Size(min = AdminAuthService.PASSWORD_MIN, max = AdminAuthService.PASSWORD_MAX) String password,
            @NotNull AdminRole role) {}

    public record UpdateAdminRequest(@NotBlank @Size(max = 100) String displayName) {}

    public record ResetPasswordRequest(
            @NotBlank @Size(min = AdminAuthService.PASSWORD_MIN, max = AdminAuthService.PASSWORD_MAX) String newPassword) {}

    public record AdminUserResponse(UUID id, String email, String displayName, AdminRole role, String status) {}

    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> list() {
        return ResponseEntity.ok(adminUserRepository.findAllByOrderByEmailAsc().stream()
                .map(AdminUserManagementController::toResponse)
                .toList());
    }

    @PostMapping
    public ResponseEntity<AdminUserResponse> create(@Valid @RequestBody CreateAdminRequest request) {
        AdminUser admin = adminAuthService.createAdmin(request.email(), request.password(), request.role(),
                request.displayName());
        return ResponseEntity.ok(toResponse(admin));
    }

    /** Change an admin's display name. */
    @PatchMapping("/{id}")
    public ResponseEntity<AdminUserResponse> update(@PathVariable UUID id,
                                                    @Valid @RequestBody UpdateAdminRequest request) {
        return ResponseEntity.ok(toResponse(adminAuthService.rename(id, request.displayName())));
    }

    /** Sets another admin's password (not your own; use change-password) and signs them out everywhere. */
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
        adminAuthService.resetPassword(SecurityUtils.currentAdminId(), id, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private static AdminUserResponse toResponse(AdminUser a) {
        return new AdminUserResponse(a.getId(), a.getEmail(), a.getDisplayName(), a.getRole(), a.getStatus());
    }
}
