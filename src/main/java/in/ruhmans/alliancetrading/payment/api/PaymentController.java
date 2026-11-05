package in.ruhmans.alliancetrading.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.payment.service.PaymentService;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Payment details the signed-in member must pay for their investment. */
@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    public PaymentController(PaymentService paymentService, UserRepository userRepository) {
        this.paymentService = paymentService;
        this.userRepository = userRepository;
    }

    public record CurrentPaymentResponse(BigDecimal amount, String vpa, String payeeName,
                                         String upiUri, String qrContentUrl) {}

    @GetMapping("/current")
    public ResponseEntity<CurrentPaymentResponse> current() {
        UUID userId = requireApproved();
        var p = paymentService.instructionsFor(userId);
        return ResponseEntity.ok(new CurrentPaymentResponse(p.amount(), p.vpa(), p.payeeName(),
                p.upiUri(), "/api/v1/payment/qr"));
    }

    @GetMapping("/qr")
    public ResponseEntity<byte[]> qr() {
        UUID userId = requireApproved();
        var qr = paymentService.qrFor(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(qr.mime()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(qr.bytes());
    }

    private UUID requireApproved() {
        UUID userId = SecurityUtils.currentUserId();
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        if (user.getStatus() != UserStatus.APPROVED) {
            throw new ApiException(ErrorCode.FORBIDDEN, "KYC approval required");
        }
        return userId;
    }
}
