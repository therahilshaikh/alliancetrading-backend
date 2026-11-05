package in.ruhmans.alliancetrading.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    // 400 rather than 401 so a wrong current password doesn't trigger the client's refresh/logout handling
    INVALID_CREDENTIALS(HttpStatus.BAD_REQUEST),
    SESSION_REPLACED(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    FEATURE_DISABLED(HttpStatus.FORBIDDEN),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST),
    OTP_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
    ACCOUNT_LOCKED(HttpStatus.TOO_MANY_REQUESTS),
    DUPLICATE_PAN(HttpStatus.CONFLICT),
    DUPLICATE_AADHAAR(HttpStatus.CONFLICT),
    NO_PAYMENT_ASSIGNMENT(HttpStatus.CONFLICT),
    PAYMENT_ACCOUNT_IN_USE(HttpStatus.CONFLICT),
    INVALID_APPROVER(HttpStatus.BAD_REQUEST),
    SMS_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT),
    MAKER_CHECKER_VIOLATION(HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    INSUFFICIENT_WITHDRAWABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    OPEN_WITHDRAWAL_EXISTS(HttpStatus.CONFLICT),
    COOLING_OFF_ACTIVE(HttpStatus.CONFLICT),
    QUOTE_EXPIRED(HttpStatus.GONE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
