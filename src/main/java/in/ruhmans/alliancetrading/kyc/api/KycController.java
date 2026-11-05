package in.ruhmans.alliancetrading.kyc.api;

import in.ruhmans.alliancetrading.common.pii.PiiMasker;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.kyc.api.dto.KycResponse;
import in.ruhmans.alliancetrading.kyc.api.dto.KycSubmitRequest;
import in.ruhmans.alliancetrading.kyc.domain.KycProfile;
import in.ruhmans.alliancetrading.kyc.service.KycService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kyc")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    @PostMapping
    public ResponseEntity<KycResponse> submit(@Valid @RequestBody KycSubmitRequest request) {
        var profile = kycService.submit(SecurityUtils.currentUserId(), request);
        return ResponseEntity.ok(toResponse(profile));
    }

    @GetMapping
    public ResponseEntity<KycResponse> getOwn() {
        var profile = kycService.getOwn(SecurityUtils.currentUserId());
        return ResponseEntity.ok(toResponse(profile));
    }

    static KycResponse toResponse(KycProfile p) {
        return new KycResponse(p.getId(), p.getFullName(), p.getDob(),
                PiiMasker.pan(p.getPanLast4()), PiiMasker.aadhaar(p.getAadhaarLast4()),
                p.getUpdatedAt(), p.getReviewedAt(), p.getRejectionReason());
    }
}
