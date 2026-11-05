package in.ruhmans.alliancetrading.auth.api;

import in.ruhmans.alliancetrading.auth.api.dto.AdminLoginRequest;
import in.ruhmans.alliancetrading.auth.api.dto.AuthResponse;
import in.ruhmans.alliancetrading.auth.service.AdminAuthService;
import in.ruhmans.alliancetrading.auth.service.SessionService;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = AdminAuthService.PASSWORD_MIN, max = AdminAuthService.PASSWORD_MAX) String newPassword) {}

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return ResponseEntity.ok(toResponse(adminAuthService.login(request.email(), request.password())));
    }

    /**
     * Any admin changes their own password. Every session of this admin (all devices) is revoked;
     * the response carries a fresh session that replaces the caller's tokens.
     */
    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(toResponse(adminAuthService.changePassword(
                SecurityUtils.currentAdminId(), request.currentPassword(), request.newPassword())));
    }

    private static AuthResponse toResponse(SessionService.SessionTokens tokens) {
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresAt(), false, tokens.admin());
    }
}
