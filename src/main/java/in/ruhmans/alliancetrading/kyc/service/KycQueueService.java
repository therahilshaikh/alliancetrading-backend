package in.ruhmans.alliancetrading.kyc.service;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import in.ruhmans.alliancetrading.bank.api.dto.BankAccountResponse;
import in.ruhmans.alliancetrading.bank.domain.BankAccount;
import in.ruhmans.alliancetrading.bank.domain.BankAccountStatus;
import in.ruhmans.alliancetrading.bank.repository.BankAccountRepository;
import in.ruhmans.alliancetrading.common.pagination.PageResponse;
import in.ruhmans.alliancetrading.common.pii.PiiMasker;
import in.ruhmans.alliancetrading.kyc.api.dto.KycQueueItem;
import in.ruhmans.alliancetrading.kyc.repository.KycProfileRepository;
import in.ruhmans.alliancetrading.kyc.repository.KycQueueRow;
import in.ruhmans.alliancetrading.payment.api.dto.PaymentAssignmentResponse;
import in.ruhmans.alliancetrading.payment.domain.PaymentAccount;
import in.ruhmans.alliancetrading.payment.domain.UserPaymentAssignment;
import in.ruhmans.alliancetrading.payment.repository.PaymentAccountRepository;
import in.ruhmans.alliancetrading.payment.repository.UserPaymentAssignmentRepository;
import in.ruhmans.alliancetrading.user.domain.UserStatus;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the admin account-approval queue with bank and payment details batched per page. */
@Service
public class KycQueueService {

    private static final EnumSet<BankAccountStatus> CURRENT_BANK_STATUSES =
            EnumSet.of(BankAccountStatus.PENDING_VERIFICATION, BankAccountStatus.VERIFIED);

    private final KycProfileRepository kycRepository;
    private final BankAccountRepository bankAccountRepository;
    private final UserPaymentAssignmentRepository assignmentRepository;
    private final PaymentAccountRepository paymentAccountRepository;

    public KycQueueService(KycProfileRepository kycRepository, BankAccountRepository bankAccountRepository,
                           UserPaymentAssignmentRepository assignmentRepository,
                           PaymentAccountRepository paymentAccountRepository) {
        this.kycRepository = kycRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.assignmentRepository = assignmentRepository;
        this.paymentAccountRepository = paymentAccountRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<KycQueueItem> queue(UserStatus status, int page, int size) {
        var rows = kycRepository.findQueue(status,
                PageRequest.of(Math.max(page, 0), PageResponse.clampSize(size)));
        List<UUID> userIds = rows.getContent().stream().map(KycQueueRow::userId).toList();
        if (userIds.isEmpty()) {
            return PageResponse.of(rows, List.of());
        }

        // Prefer the account awaiting review; otherwise show the verified one
        Map<UUID, BankAccount> banks = bankAccountRepository
                .findByUserIdInAndStatusIn(userIds, CURRENT_BANK_STATUSES).stream()
                .collect(Collectors.toMap(BankAccount::getUserId, Function.identity(),
                        (a, b) -> pickCurrent(a, b)));
        Map<UUID, UserPaymentAssignment> assignments = assignmentRepository
                .findByUserIdInAndActiveTrue(userIds).stream()
                .collect(Collectors.toMap(UserPaymentAssignment::getUserId, Function.identity()));
        Map<UUID, PaymentAccount> accounts = paymentAccountRepository
                .findAllById(assignments.values().stream().map(UserPaymentAssignment::getPaymentAccountId).toList())
                .stream().collect(Collectors.toMap(PaymentAccount::getId, Function.identity()));

        List<KycQueueItem> items = rows.getContent().stream().map(row -> {
            BankAccount bank = banks.get(row.userId());
            UserPaymentAssignment assignment = assignments.get(row.userId());
            return new KycQueueItem(row.userId(), row.mobile(), row.status(), row.fullName(), row.dob(),
                    PiiMasker.pan(row.panLast4()), PiiMasker.aadhaar(row.aadhaarLast4()),
                    row.submittedAt(), row.reviewedAt(), row.rejectionReason(),
                    bank != null ? BankAccountResponse.from(bank) : null,
                    assignment != null
                            ? PaymentAssignmentResponse.from(assignment, accounts.get(assignment.getPaymentAccountId()))
                            : null);
        }).toList();
        return PageResponse.of(rows, items);
    }

    private static BankAccount pickCurrent(BankAccount a, BankAccount b) {
        if (a.getStatus() != b.getStatus()) {
            return a.getStatus() == BankAccountStatus.PENDING_VERIFICATION ? a : b;
        }
        return Comparator.comparing(BankAccount::getCreatedAt).compare(a, b) >= 0 ? a : b;
    }
}
