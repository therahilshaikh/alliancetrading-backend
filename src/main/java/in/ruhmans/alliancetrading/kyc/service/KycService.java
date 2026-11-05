package in.ruhmans.alliancetrading.kyc.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.common.crypto.FieldEncryptor;
import in.ruhmans.alliancetrading.common.crypto.FieldHasher;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.kyc.api.dto.KycSubmitRequest;
import in.ruhmans.alliancetrading.kyc.domain.KycProfile;
import in.ruhmans.alliancetrading.kyc.repository.KycProfileRepository;
import in.ruhmans.alliancetrading.notification.service.OutboxService;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KycService {

    static final int MIN_AGE_YEARS = 18;

    private final KycProfileRepository kycRepository;
    private final UserRepository userRepository;
    private final FieldEncryptor fieldEncryptor;
    private final FieldHasher fieldHasher;
    private final AuditService auditService;
    private final OutboxService outboxService;
    private final Clock clock;

    public KycService(KycProfileRepository kycRepository, UserRepository userRepository,
                      FieldEncryptor fieldEncryptor, FieldHasher fieldHasher,
                      AuditService auditService, OutboxService outboxService, Clock clock) {
        this.kycRepository = kycRepository;
        this.userRepository = userRepository;
        this.fieldEncryptor = fieldEncryptor;
        this.fieldHasher = fieldHasher;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    @Transactional
    public KycProfile submit(UUID userId, KycSubmitRequest request) {
        Instant now = clock.instant();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

        // if (user.getStatus() != UserStatus.REGISTERED && user.getStatus() != UserStatus.REJECTED) {
        //     throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
        //             "KYC can only be submitted from REGISTERED or REJECTED status");
        // }

        if (Period.between(request.dob(), LocalDate.ofInstant(now, clock.getZone())).getYears() < MIN_AGE_YEARS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "You must be at least " + MIN_AGE_YEARS + " years old");
        }
        if (!AadhaarValidator.isValid(request.aadhaar())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid Aadhaar number");
        }

        String panHash = fieldHasher.hash(request.pan());
        String aadhaarHash = fieldHasher.hash(request.aadhaar());
        // Resubmission by the same user reuses its row; numbers owned by another user are rejected
        var existing = kycRepository.findByUserId(userId);
        kycRepository.findByPanHash(panHash)
                .filter(other -> !other.getUserId().equals(userId))
                .ifPresent(other -> {
                    throw new ApiException(ErrorCode.DUPLICATE_PAN, "PAN already registered");
                });
        kycRepository.findByAadhaarHash(aadhaarHash)
                .filter(other -> !other.getUserId().equals(userId))
                .ifPresent(other -> {
                    throw new ApiException(ErrorCode.DUPLICATE_AADHAAR, "Aadhaar already registered");
                });

        var ids = new KycProfile.IdentityNumbers(
                fieldEncryptor.encrypt(request.pan()), panHash, last4(request.pan()),
                fieldEncryptor.encrypt(request.aadhaar()), aadhaarHash, last4(request.aadhaar()));

        KycProfile profile = existing.orElseGet(() -> new KycProfile(userId, now));
        profile.fill(request.fullName().trim(), request.dob(), ids,
                KycProfile.SupportingDocuments.NONE, now);
        kycRepository.save(profile);

        user.setStatus(UserStatus.KYC_SUBMITTED);
        user.setUpdatedAt(now);
        userRepository.save(user);

        auditService.recordUser(userId, "KYC_SUBMITTED", "kyc_profile", profile.getId().toString(),
                null, null);
        return profile;
    }

    @Transactional(readOnly = true)
    public KycProfile getOwn(UUID userId) {
        return kycRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "KYC not submitted"));
    }

    @Transactional
    public void approve(UUID userId, UUID adminId) {
        Instant now = clock.instant();
        KycProfile profile = kycRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "KYC not found"));
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

        if (user.getStatus() != UserStatus.KYC_SUBMITTED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "KYC is not in KYC_SUBMITTED state");
        }

        profile.markReviewed(adminId, null, now);
        kycRepository.save(profile);

        user.setStatus(UserStatus.APPROVED);
        user.setUpdatedAt(now);
        userRepository.save(user);

        auditService.recordAdmin(adminId, "KYC_APPROVED", "user", userId.toString(), null, null);
        outboxService.notifyUser("KYC_APPROVED", userId, "{}");
    }

    @Transactional
    public void reject(UUID userId, UUID adminId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Rejection reason is required");
        }
        Instant now = clock.instant();
        KycProfile profile = kycRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "KYC not found"));
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

        if (user.getStatus() != UserStatus.KYC_SUBMITTED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "KYC is not in KYC_SUBMITTED state");
        }

        profile.markReviewed(adminId, reason, now);
        kycRepository.save(profile);

        user.setStatus(UserStatus.REJECTED);
        user.setUpdatedAt(now);
        userRepository.save(user);

        auditService.recordAdmin(adminId, "KYC_REJECTED", "user", userId.toString(),
                null, AuditService.json("reason", reason));
        outboxService.notifyUser("KYC_REJECTED", userId, "{}");
    }

    public enum IdentityField { PAN, AADHAAR }

    /** Full identity number for admin review; every view is audited. */
    @Transactional
    public String revealIdentityNumber(UUID userId, IdentityField field, UUID adminId) {
        KycProfile profile = kycRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "KYC not found"));
        String encrypted = field == IdentityField.PAN ? profile.getPanEnc() : profile.getAadhaarEnc();
        if (encrypted == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, field + " not on file");
        }
        auditService.recordAdmin(adminId, "PII_VIEWED", "kyc_profile", profile.getId().toString(),
                null, AuditService.json("field", field.name().toLowerCase()));
        return fieldEncryptor.decrypt(encrypted);
    }

    private static String last4(String value) {
        return value.substring(value.length() - 4);
    }
}
