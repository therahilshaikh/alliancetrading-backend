package in.ruhmans.alliancetrading.user.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.UUID;

import in.ruhmans.alliancetrading.bank.api.dto.BankAccountResponse;
import in.ruhmans.alliancetrading.bank.domain.BankAccount;
import in.ruhmans.alliancetrading.bank.domain.BankAccountStatus;
import in.ruhmans.alliancetrading.bank.repository.BankAccountRepository;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.common.pii.PiiMasker;
import in.ruhmans.alliancetrading.kyc.repository.KycProfileRepository;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The member's own profile screen: identity (masked), payout bank account and UPI ID. */
@Service
public class ProfileService {

    public record Profile(UUID userId, String mobile, UserStatus status, Instant joinedAt,
                          String fullName, LocalDate dob, String panMasked, String aadhaarMasked,
                          boolean kycVerified, BankAccountResponse bankAccount) {}

    private final UserRepository userRepository;
    private final KycProfileRepository kycRepository;
    private final BankAccountRepository bankAccountRepository;

    public ProfileService(UserRepository userRepository, KycProfileRepository kycRepository,
                          BankAccountRepository bankAccountRepository) {
        this.userRepository = userRepository;
        this.kycRepository = kycRepository;
        this.bankAccountRepository = bankAccountRepository;
    }

    @Transactional(readOnly = true)
    public Profile profile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        var kyc = kycRepository.findByUserId(userId).orElse(null);
        BankAccount bank = currentBankAccount(userId);
        return new Profile(user.getId(), user.getMobile(), user.getStatus(), user.getCreatedAt(),
                kyc != null ? kyc.getFullName() : null,
                kyc != null ? kyc.getDob() : null,
                kyc != null ? PiiMasker.pan(kyc.getPanLast4()) : null,
                kyc != null ? PiiMasker.aadhaar(kyc.getAadhaarLast4()) : null,
                user.getStatus() == UserStatus.APPROVED,
                bank != null ? BankAccountResponse.from(bank) : null);
    }

    /** The verified payout account, or the latest one awaiting review. */
    private BankAccount currentBankAccount(UUID userId) {
        return bankAccountRepository.findByUserIdAndStatus(userId, BankAccountStatus.VERIFIED)
                .orElseGet(() -> bankAccountRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .filter(b -> b.getStatus() == BankAccountStatus.PENDING_VERIFICATION)
                        .max(Comparator.comparing(BankAccount::getCreatedAt))
                        .orElse(null));
    }
}
