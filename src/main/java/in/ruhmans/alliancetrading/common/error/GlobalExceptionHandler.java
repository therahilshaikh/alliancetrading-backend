package in.ruhmans.alliancetrading.common.error;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ProblemDetail problem(ErrorCode code, String detail, Map<String, Object> props) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), detail);
        pd.setTitle(code.name().replace('_', ' '));
        pd.setType(URI.create("about:blank"));
        pd.setProperty("code", code.name());
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            pd.setProperty("requestId", requestId);
        }
        props.forEach(pd::setProperty);
        return pd;
    }

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex) {
        if (ex.code().status().is5xxServerError()) {
            log.error("API error {}: {}", ex.code(), ex.getMessage(), ex);
        } else {
            log.warn("API error {}: {}", ex.code(), ex.getMessage());
        }
        return problem(ex.code(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::fieldError)
                .toList();
        return problem(ErrorCode.VALIDATION_FAILED, "Request validation failed", Map.of("errors", errors));
    }

    private Map<String, String> fieldError(FieldError fe) {
        return Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return problem(ErrorCode.VALIDATION_FAILED, "Malformed request body", Map.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        return problem(ErrorCode.VALIDATION_FAILED, "Missing required header: " + ex.getHeaderName(), Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleDenied(AccessDeniedException ex) {
        return problem(ErrorCode.FORBIDDEN, "Access denied", Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadSize(MaxUploadSizeExceededException ex) {
        return problem(ErrorCode.FILE_TOO_LARGE, "Uploaded file exceeds the maximum allowed size", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleOther(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setProperty("code", ErrorCode.INTERNAL_ERROR.name());
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            pd.setProperty("requestId", requestId);
        }
        return pd;
    }
}
