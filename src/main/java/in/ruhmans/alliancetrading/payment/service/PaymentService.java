package in.ruhmans.alliancetrading.payment.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.common.money.MoneyUtils;
import in.ruhmans.alliancetrading.files.domain.DocType;
import in.ruhmans.alliancetrading.files.repository.DocumentRepository;
import in.ruhmans.alliancetrading.files.service.FileService;
import in.ruhmans.alliancetrading.investment.domain.InvestmentStatus;
import in.ruhmans.alliancetrading.investment.repository.InvestmentRepository;
import in.ruhmans.alliancetrading.payment.domain.PaymentAccount;
import in.ruhmans.alliancetrading.payment.domain.PaymentAccountStatus;
import in.ruhmans.alliancetrading.payment.domain.UserPaymentAssignment;
import in.ruhmans.alliancetrading.payment.repository.PaymentAccountRepository;
import in.ruhmans.alliancetrading.payment.repository.UserPaymentAssignmentRepository;
import in.ruhmans.alliancetrading.settings.service.SettingsService;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saved company payment accounts and the per-user assignment of which account and
 * amount each member pays for their investment.
 */
@Service
public class PaymentService {

    private final PaymentAccountRepository accountRepository;
    private final UserPaymentAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final FileService fileService;
    private final SettingsService settingsService;
    private final UpiQrGenerator qrGenerator;
    private final AuditService auditService;
    private final InvestmentRepository investmentRepository;
    private final Clock clock;

    public PaymentService(PaymentAccountRepository accountRepository,
                          UserPaymentAssignmentRepository assignmentRepository,
                          UserRepository userRepository, DocumentRepository documentRepository,
                          FileService fileService, SettingsService settingsService,
                          UpiQrGenerator qrGenerator, AuditService auditService,
                          InvestmentRepository investmentRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.fileService = fileService;
        this.settingsService = settingsService;
        this.qrGenerator = qrGenerator;
        this.auditService = auditService;
        this.investmentRepository = investmentRepository;
        this.clock = clock;
    }

    /** Details for a payment account that does not exist yet. */
    public record NewAccount(String label, String vpa, String payeeName, UUID qrDocumentId) {}

    /**
     * Either {@code paymentAccountId} (pick from the saved list) or {@code newAccount}
     * (enter new details) must be given. {@code saveToList} controls whether a new account
     * appears in the dropdown afterwards. {@code amount} defaults to the configured amount.
     */
    public record AssignmentCommand(UUID paymentAccountId, NewAccount newAccount,
                                    boolean saveToList, BigDecimal amount) {}

    /** What the member sees on the payment screen. */
    public record PaymentInstructions(UUID assignmentId, BigDecimal amount, String vpa,
                                      String payeeName, String upiUri) {}

    public record QrImage(byte[] bytes, String mime) {}

    /** The UPI ID and amount a member was told to pay under one assignment. */
    public record ExpectedPayment(String vpa, BigDecimal amount) {}

    /** Expected payment per assignment ID, in two queries regardless of how many IDs are given. */
    @Transactional(readOnly = true)
    public Map<UUID, ExpectedPayment> expectedByAssignmentId(Collection<UUID> assignmentIds) {
        if (assignmentIds.isEmpty()) {
            return Map.of();
        }
        List<UserPaymentAssignment> assignments = assignmentRepository.findAllById(assignmentIds);
        Map<UUID, String> vpaByAccount = new HashMap<>();
        for (PaymentAccount a : accountRepository.findAllById(
                assignments.stream().map(UserPaymentAssignment::getPaymentAccountId).collect(Collectors.toSet()))) {
            vpaByAccount.put(a.getId(), a.getVpa());
        }
        Map<UUID, ExpectedPayment> expected = new HashMap<>();
        for (UserPaymentAssignment a : assignments) {
            expected.put(a.getId(), new ExpectedPayment(vpaByAccount.get(a.getPaymentAccountId()), a.getAmount()));
        }
        return expected;
    }

    // ---- Saved accounts ----

    @Transactional(readOnly = true)
    public List<PaymentAccount> listAccounts(PaymentAccountStatus status) {
        return accountRepository.findByStatusOrderByLabelAsc(status);
    }

    @Transactional
    public PaymentAccount createAccount(UUID adminId, NewAccount details) {
        return saveAccount(adminId, details, true);
    }

    @Transactional
    public void archiveAccount(UUID accountId, UUID adminId) {
        PaymentAccount account = requireAccount(accountId);
        if (!account.isActive()) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION, "Payment account is already archived");
        }
        if (settingsService.isDefaultPaymentAccount(accountId)) {
            throw new ApiException(ErrorCode.PAYMENT_ACCOUNT_IN_USE,
                    "This account is the default payment account (now or in a scheduled change); "
                    + "choose another default in Settings first");
        }
        account.archive(adminId, clock.instant());
        accountRepository.save(account);
        auditService.recordAdmin(adminId, "PAYMENT_ACCOUNT_ARCHIVED", "payment_account",
                accountId.toString(), null, null);
    }

    /**
     * The account for a default payment setting: an ACTIVE saved account, or a new one created now. A new
     * account is always saved to the list, because the setting references it.
     */
    @Transactional
    public PaymentAccount resolveDefaultAccount(UUID adminId, UUID paymentAccountId, NewAccount newAccount) {
        return resolveAccount(adminId, new AssignmentCommand(paymentAccountId, newAccount, true, null));
    }

    // ---- Assignments ----

    @Transactional
    public UserPaymentAssignment assign(UUID userId, UUID adminId, AssignmentCommand command) {
        Instant now = clock.instant();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        if (user.getStatus() != UserStatus.KYC_SUBMITTED && user.getStatus() != UserStatus.APPROVED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Payment details can only be assigned to users under review or approved");
        }

        PaymentAccount account = resolveAccount(adminId, command);
        BigDecimal amount = resolveAmount(command.amount());

        assignmentRepository.findByUserIdAndActiveTrue(userId).ifPresent(previous -> {
            previous.deactivate(now);
            assignmentRepository.saveAndFlush(previous);
        });
        UserPaymentAssignment assignment = assignmentRepository.save(
                new UserPaymentAssignment(userId, account.getId(), amount, adminId, now));

        auditService.recordAdmin(adminId, "PAYMENT_ASSIGNED", "user", userId.toString(), null,
                AuditService.json("assignmentId", assignment.getId(),
                        "paymentAccountId", account.getId(), "vpa", account.getVpa(),
                        "amount", amount));
        return assignment;
    }

    /** Cancels the active assignment before the member pays; refused while a proof is under review. */
    @Transactional
    public void cancelAssignment(UUID userId, UUID adminId) {
        Instant now = clock.instant();
        if (investmentRepository.existsByUserIdAndStatus(userId, InvestmentStatus.PENDING_VERIFICATION)) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A payment proof is under review; approve or reject it first");
        }
        UserPaymentAssignment assignment = assignmentRepository.findByUserIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NO_PAYMENT_ASSIGNMENT,
                        "There is no active assignment to cancel"));
        assignment.deactivate(now);
        assignmentRepository.save(assignment);
        auditService.recordAdmin(adminId, "PAYMENT_ASSIGNMENT_CANCELLED", "user", userId.toString(),
                null, AuditService.json("assignmentId", assignment.getId(),
                        "amount", assignment.getAmount()));
    }

    /** Marks the assignment consumed once its payment has been verified (investment approved). */
    @Transactional
    public void consumeAssignment(UUID assignmentId, UUID adminId) {
        UserPaymentAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Assignment not found"));
        if (!assignment.isActive()) {
            return;
        }
        assignment.deactivate(clock.instant());
        assignmentRepository.save(assignment);
        auditService.recordAdmin(adminId, "PAYMENT_ASSIGNMENT_FULFILLED", "user",
                assignment.getUserId().toString(), null,
                AuditService.json("assignmentId", assignment.getId(), "amount", assignment.getAmount()));
    }

    @Transactional(readOnly = true)
    public UserPaymentAssignment requireActiveAssignment(UUID userId) {
        return assignmentRepository.findByUserIdAndActiveTrue(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NO_PAYMENT_ASSIGNMENT,
                        "Payment details have not been assigned yet"));
    }

    @Transactional(readOnly = true)
    public PaymentInstructions instructionsFor(UUID userId) {
        UserPaymentAssignment assignment = requireActiveAssignment(userId);
        PaymentAccount account = requireAccount(assignment.getPaymentAccountId());
        return new PaymentInstructions(assignment.getId(), assignment.getAmount(),
                account.getVpa(), account.getPayeeName(), upiUri(account, assignment));
    }

    /** The account's uploaded QR image if it has one, otherwise a generated UPI QR. */
    @Transactional(readOnly = true)
    public QrImage qrFor(UUID userId) {
        UserPaymentAssignment assignment = requireActiveAssignment(userId);
        PaymentAccount account = requireAccount(assignment.getPaymentAccountId());
        if (account.getQrDocumentId() != null) {
            var content = fileService.getSharedContent(account.getQrDocumentId());
            return new QrImage(content.bytes(), content.mime());
        }
        return new QrImage(qrGenerator.renderPng(upiUri(account, assignment)), "image/png");
    }

    @Transactional(readOnly = true)
    public PaymentAccount requireAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Payment account not found"));
    }

    // ---- Internal ----

    private PaymentAccount resolveAccount(UUID adminId, AssignmentCommand command) {
        boolean pickExisting = command.paymentAccountId() != null;
        boolean enterNew = command.newAccount() != null;
        if (pickExisting == enterNew) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Provide either paymentAccountId or newAccount, not both");
        }
        if (pickExisting) {
            PaymentAccount account = requireAccount(command.paymentAccountId());
            if (!account.isActive()) {
                throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION, "Payment account is archived");
            }
            return account;
        }
        return saveAccount(adminId, command.newAccount(), command.saveToList());
    }

    /**
     * Persists a new account. Accounts not saved to the list are archived immediately:
     * they back the assignment but never show up in the dropdown.
     */
    private PaymentAccount saveAccount(UUID adminId, NewAccount details, boolean listed) {
        Instant now = clock.instant();
        String vpa = details.vpa().trim();
        if (listed && accountRepository.existsActiveByVpa(vpa)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "UPI ID " + vpa + " is already saved; select it from the list");
        }
        if (details.qrDocumentId() != null) {
            documentRepository.findById(details.qrDocumentId())
                    .filter(d -> d.getDocType() == DocType.PAYMENT_QR && d.getOwnerUserId() == null)
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                            "QR document must be an admin-uploaded PAYMENT_QR"));
        }

        String label = details.label() == null || details.label().isBlank()
                ? vpa : details.label().trim();
        PaymentAccount account = new PaymentAccount(label, vpa, details.payeeName().trim(),
                details.qrDocumentId(), adminId, now);
        if (!listed) {
            account.archive(adminId, now);
        }
        try {
            accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "UPI ID " + vpa + " is already saved");
        }
        auditService.recordAdmin(adminId, "PAYMENT_ACCOUNT_CREATED", "payment_account",
                account.getId().toString(), null,
                AuditService.json("label", label, "vpa", vpa, "listed", listed));
        return account;
    }

    private BigDecimal resolveAmount(BigDecimal requested) {
        BigDecimal amount = requested != null ? requested
                : settingsService.defaultInvestmentAmount()
                        .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                                "Amount is required (no default payment setting configured)"));
        amount = MoneyUtils.money(amount);
        if (amount.signum() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount must be positive");
        }
        return amount;
    }

    private String upiUri(PaymentAccount account, UserPaymentAssignment assignment) {
        return qrGenerator.payload(account.getVpa(), account.getPayeeName(), assignment.getAmount(),
                "AllianceTrading investment");
    }
}
