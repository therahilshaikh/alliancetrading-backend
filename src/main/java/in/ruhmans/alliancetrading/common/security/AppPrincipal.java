package in.ruhmans.alliancetrading.common.security;

import java.util.UUID;

public record AppPrincipal(UUID subjectId, UUID sessionId, String role, String type) {

    public boolean isUser() {
        return "USER".equals(type);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(type);
    }

    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(role);
    }
}
