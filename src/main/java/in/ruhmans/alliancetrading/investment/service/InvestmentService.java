package in.ruhmans.alliancetrading.investment.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.bank.domain.BankAccountStatus;
import in.ruhmans.alliancetrading.bank.repository.BankAccountRepository;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.files.domain.DocType;
import in.ruhmans.alliancetrading.files.repository.DocumentRepository;
import in.ruhmans.alliancetrading.investment.api.dto.InvestmentRequest;
import in.ruhmans.alliancetrading.investment.domain.Investment;
import in.ruhmans.alliancetrading.investment.domain.InvestmentStatus;
import in.ruhmans.alliancetrading.investment.repository.InvestmentRepository;
import in.ruhmans.alliancetrading.ledger.domain.Bucket;
import in.ruhmans.alliancetrading.ledger.domain.EntryType;
import in.ruhmans.alliancetrading.ledger.service.LedgerPostingService;
import in.ruhmans.alliancetrading.notification.service.OutboxService;
import in.ruhmans.alliancetrading.payment.service.PaymentService;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestmentService {

    private final InvestmentRepository investmentRepository;
    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;
    private final DocumentRepository documentRepository;
    private final PaymentService paymentService;
    private final LedgerPostingService ledgerPostingService;
    private final AuditService auditService;
    private final OutboxService outboxService;
    private final Clock clock;

    public InvestmentService(InvestmentRepository investmentRepository,
                             UserRepository userRepository,
                             BankAccountRepository bankAccountRepository,
                             DocumentRepository documentRepository,
                             PaymentService paymentService,
                             LedgerPostingService ledgerPostingService,
                             AuditService auditService, OutboxService outboxService, Clock clock) {
        this.investmentRepository = investmentRepository;
        this.userRepository = userRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.documentRepository = documentRepository;
        this.paymentService = paymentService;
        this.ledgerPostingService = ledgerPostingService;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    @Transactional
    public Investment submit(UUID userId, InvestmentRequest request, String idempotencyKey) {
        Instant now = clock.instant();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

        if (user.getStatus() != UserStatus.APPROVED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only APPROVED users can invest");
        }

        boolean hasVerifiedBank = bankAccountRepository
                .findByUserIdAndStatus(userId, BankAccountStatus.VERIFIED).isPresent();
        if (!hasVerifiedBank) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A VERIFIED bank account is required to invest");
        }

        if (investmentRepository.existsByUserIdAndStatus(userId, InvestmentStatus.PENDING_VERIFICATION)) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A payment proof is already under review; wait for it to be verified");
        }

        var assignment = paymentService.requireActiveAssignment(userId);

        documentRepository.findById(request.proofDocumentId())
                .filter(d -> userId.equals(d.getOwnerUserId()) && d.getDocType() == DocType.PAYMENT_PROOF)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Payment proof not found or not owned by user"));

        Investment investment = new Investment(userId, assignment.getAmount(), assignment.getId(),
                request.proofDocumentId(), now);
        try {
            investmentRepository.saveAndFlush(investment);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "A payment proof is already under review; wait for it to be verified");
        }

        auditService.recordUser(userId, "INVESTMENT_SUBMITTED", "investment",
                investment.getId().toString(), null,
                AuditService.json("amount", investment.getAmount(), "paymentAssignmentId", assignment.getId()));
        return investment;
    }

    @Transactional(readOnly = true)
    public List<Investment> getOwn(UUID userId) {
        return investmentRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ---- Admin ----

    @Transactional(readOnly = true)
    public Page<Investment> listByStatus(InvestmentStatus status, Pageable pageable) {
        return investmentRepository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public Investment getById(UUID id) {
        return investmentRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Investment not found"));
    }

    @Transactional
    public void approve(UUID investmentId, UUID adminId) {
        Instant now = clock.instant();
        Investment investment = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Investment not found"));

        if (investment.getStatus() != InvestmentStatus.PENDING_VERIFICATION) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Investment is not pending verification");
        }

        investment.approve(adminId, now);
        investmentRepository.save(investment);

        // The payment request is fulfilled; the member cannot pay again until the admin assigns anew
        if (investment.getPaymentAssignmentId() != null) {
            paymentService.consumeAssignment(investment.getPaymentAssignmentId(), adminId);
        }

        // Post principal to the ledger
        ledgerPostingService.post(new LedgerPostingService.Journal(
                investment.getUserId(), investment.getId(),
                List.of(new LedgerPostingService.Posting(Bucket.PRINCIPAL, EntryType.DEPOSIT,
                        investment.getAmount())),
                "investment", investment.getId().toString(),
                "investment-approve:" + investment.getId(),
                "ADMIN", adminId));

        auditService.recordAdmin(adminId, "INVESTMENT_APPROVED", "investment",
                investmentId.toString(), null, null);
        outboxService.notifyUser("INVESTMENT_APPROVED", investment.getUserId(), "{}");
    }

    @Transactional
    public void reject(UUID investmentId, UUID adminId, String reason) {
        Instant now = clock.instant();
        Investment investment = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Investment not found"));

        if (investment.getStatus() != InvestmentStatus.PENDING_VERIFICATION) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Investment is not pending verification");
        }

        investment.reject(reason, now);
        investmentRepository.save(investment);

        auditService.recordAdmin(adminId, "INVESTMENT_REJECTED", "investment",
                investmentId.toString(), null, AuditService.json("reason", reason));
        outboxService.notifyUser("INVESTMENT_REJECTED", investment.getUserId(), "{}");
    }
}
