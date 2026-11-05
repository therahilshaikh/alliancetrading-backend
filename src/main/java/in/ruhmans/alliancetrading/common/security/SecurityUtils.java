package in.ruhmans.alliancetrading.common.security;

import java.util.UUID;

import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static AppPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppPrincipal p)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Not authenticated");
        }
        return p;
    }

    public static UUID currentUserId() {
        AppPrincipal p = currentPrincipal();
        if (!p.isUser()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "User context required");
        }
        return p.subjectId();
    }

    public static UUID currentAdminId() {
        AppPrincipal p = currentPrincipal();
        if (!p.isAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Admin context required");
        }
        return p.subjectId();
    }

    public static UUID currentSessionId() {
        return currentPrincipal().sessionId();
    }
}
