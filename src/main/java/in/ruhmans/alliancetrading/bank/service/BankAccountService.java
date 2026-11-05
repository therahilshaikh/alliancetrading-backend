package in.ruhmans.alliancetrading.bank.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.bank.api.dto.BankAccountRequest;
import in.ruhmans.alliancetrading.bank.domain.BankAccount;
import in.ruhmans.alliancetrading.bank.domain.BankAccountStatus;
import in.ruhmans.alliancetrading.bank.repository.BankAccountRepository;
import in.ruhmans.alliancetrading.common.crypto.FieldEncryptor;
import in.ruhmans.alliancetrading.common.crypto.FieldHasher;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.files.repository.DocumentRepository;
import in.ruhmans.alliancetrading.settings.service.SettingsService;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final FieldEncryptor fieldEncryptor;
    private final FieldHasher fieldHasher;
    private final AuditService auditService;
    private final SettingsService settingsService;
    private final Clock clock;

    public BankAccountService(BankAccountRepository bankAccountRepository,
                              UserRepository userRepository,
                              DocumentRepository documentRepository,
                              FieldEncryptor fieldEncryptor, FieldHasher fieldHasher,
                              AuditService auditService, SettingsService settingsService, Clock clock) {
        this.bankAccountRepository = bankAccountRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.fieldEncryptor = fieldEncryptor;
        this.fieldHasher = fieldHasher;
        this.auditService = auditService;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Transactional
    public BankAccount submit(UUID userId, BankAccountRequest request) {
        Instant now = clock.instant();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Suspended users cannot submit bank accounts");
        }

        if (request.proofDocumentId() != null) {
            var proof = documentRepository.findById(request.proofDocumentId())
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "Proof document not found"));
            if (proof.getOwnerUserId() == null || !proof.getOwnerUserId().equals(userId)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Proof document does not belong to the user");
            }
        }

        // A newer submission supersedes any account still awaiting review
        bankAccountRepository.findByUserIdAndStatus(userId, BankAccountStatus.PENDING_VERIFICATION)
                .ifPresent(pending -> {
                    pending.markReplaced(now);
                    bankAccountRepository.save(pending);
                });

        String enc = fieldEncryptor.encrypt(request.accountNumber());
        String hash = fieldHasher.hash(request.accountNumber());
        String last4 = request.accountNumber().substring(request.accountNumber().length() - 4);
        // Cooling-off only protects changes to an already-verified payout account
        boolean isChange = bankAccountRepository
                .findByUserIdAndStatus(userId, BankAccountStatus.VERIFIED).isPresent();
        Instant cooloffUntil = isChange
                ? now.plus(Duration.ofHours(settingsService.bankChangeCooloffHours()))
                : null;

        BankAccount account = new BankAccount(userId, request.holderName().trim(), enc, hash, last4,
                request.ifsc(), request.accountType(), request.vpa().trim(),
                request.proofDocumentId(), cooloffUntil, now);
        bankAccountRepository.save(account);

        auditService.recordUser(userId, "BANK_ACCOUNT_SUBMITTED", "bank_account",
                account.getId().toString(), null, null);
        return account;
    }

    @Transactional(readOnly = true)
    public List<BankAccount> getOwn(UUID userId) {
        return bankAccountRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ---- Admin operations ----

    @Transactional(readOnly = true)
    public Page<BankAccount> listByStatus(BankAccountStatus status, Pageable pageable) {
        return bankAccountRepository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public BankAccount getById(UUID id) {
        return bankAccountRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Bank account not found"));
    }

    @Transactional
    public void verify(UUID accountId, UUID adminId) {
        Instant now = clock.instant();
        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Bank account not found"));
        if (account.getStatus() != BankAccountStatus.PENDING_VERIFICATION) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Account is not pending verification");
        }

        // Replace the currently-verified account, if any
        bankAccountRepository.findByUserIdAndStatus(account.getUserId(), BankAccountStatus.VERIFIED)
                .ifPresent(old -> {
                    old.markReplaced(now);
                    bankAccountRepository.save(old);
                });

        account.markVerified(adminId, now);
        bankAccountRepository.save(account);
        auditService.recordAdmin(adminId, "BANK_ACCOUNT_VERIFIED", "bank_account",
                accountId.toString(), null, null);
    }

    /**
     * Verifies the user's pending bank account as part of account approval.
     * Returns false (and leaves it pending) when there is none.
     */
    @Transactional
    public boolean verifyPendingIfEligible(UUID userId, UUID adminId) {
        var pending = bankAccountRepository.findByUserIdAndStatus(userId, BankAccountStatus.PENDING_VERIFICATION);
        if (pending.isEmpty()) {
            return false;
        }
        verify(pending.get().getId(), adminId);
        return true;
    }

    @Transactional
    public void reject(UUID accountId, UUID adminId, String reason) {
        Instant now = clock.instant();
        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Bank account not found"));
        if (account.getStatus() != BankAccountStatus.PENDING_VERIFICATION) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Account is not pending verification");
        }
        account.markRejected(adminId, reason, now);
        bankAccountRepository.save(account);
        auditService.recordAdmin(adminId, "BANK_ACCOUNT_REJECTED", "bank_account",
                accountId.toString(), null, AuditService.json("reason", reason));
    }

    @Transactional
    public String getDecryptedAccountNumber(UUID accountId, UUID adminId) {
        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Bank account not found"));
        auditService.recordAdmin(adminId, "PII_VIEWED", "bank_account",
                accountId.toString(), null, "{\"field\":\"account_number\"}");
        return fieldEncryptor.decrypt(account.getAccountNumberEnc());
    }
}
