package in.ruhmans.alliancetrading.auth.domain;

public enum RevokeReason {
    SESSION_REPLACED,
    LOGOUT,
    REFRESH_TOKEN_REUSE,
    FORCE_LOGOUT,
    ADMIN_ACTION,
    PASSWORD_CHANGED,
    PASSWORD_RESET
}
