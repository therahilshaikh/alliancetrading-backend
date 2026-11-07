package in.ruhmans.alliancetrading.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.bank.domain.BankAccount;
import in.ruhmans.alliancetrading.bank.domain.BankAccountStatus;
import in.ruhmans.alliancetrading.bank.repository.BankAccountRepository;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.files.domain.DocType;
import in.ruhmans.alliancetrading.files.domain.Document;
import in.ruhmans.alliancetrading.files.repository.DocumentRepository;
import in.ruhmans.alliancetrading.investment.api.dto.InvestmentRequest;
import in.ruhmans.alliancetrading.investment.domain.Investment;
import in.ruhmans.alliancetrading.investment.domain.InvestmentStatus;
import in.ruhmans.alliancetrading.investment.repository.InvestmentRepository;
import in.ruhmans.alliancetrading.investment.service.InvestmentService;
import in.ruhmans.alliancetrading.ledger.service.LedgerPostingService;
import in.ruhmans.alliancetrading.notification.service.OutboxService;
import in.ruhmans.alliancetrading.payment.domain.UserPaymentAssignment;
import in.ruhmans.alliancetrading.payment.service.PaymentService;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InvestmentServiceTest {

    private InvestmentRepository investmentRepository;
    private UserRepository userRepository;
    private BankAccountRepository bankAccountRepository;
    private DocumentRepository documentRepository;
    private PaymentService paymentService;
    private LedgerPostingService ledgerPostingService;
    private InvestmentService investmentService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        investmentRepository = Mockito.mock(InvestmentRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        bankAccountRepository = Mockito.mock(BankAccountRepository.class);
        documentRepository = Mockito.mock(DocumentRepository.class);
        paymentService = Mockito.mock(PaymentService.class);
        ledgerPostingService = Mockito.mock(LedgerPostingService.class);
        clock = Clock.fixed(Instant.now(), ZoneId.of("Asia/Kolkata"));

        investmentService = new InvestmentService(investmentRepository, userRepository,
                bankAccountRepository, documentRepository, paymentService, ledgerPostingService,
                Mockito.mock(AuditService.class),
                Mockito.mock(OutboxService.class), clock);
    }

    private User approvedUserWithBank() {
        User user = new User("9876543210", clock.instant());
        user.setStatus(UserStatus.APPROVED);
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(bankAccountRepository.findByUserIdAndStatus(user.getId(), BankAccountStatus.VERIFIED))
                .thenReturn(Optional.of(Mockito.mock(BankAccount.class)));
        return user;
    }

    private UUID paymentProofOwnedBy(UUID userId, DocType type) {
        UUID docId = UUID.randomUUID();
        Document proof = new Document(userId, type, "b", "k", "image/jpeg",
                10, "a".repeat(64), "w", "k1", "USER", userId, clock.instant());
        when(documentRepository.findById(docId)).thenReturn(Optional.of(proof));
        return docId;
    }

    private Investment pendingInvestment() {
        return new Investment(UUID.randomUUID(), new BigDecimal("10000"), UUID.randomUUID(),
                UUID.randomUUID(), clock.instant());
    }

    @Test
    void submitTakesAmountFromUsersPaymentAssignment() {
        User user = approvedUserWithBank();
        var assignment = new UserPaymentAssignment(user.getId(), UUID.randomUUID(),
                new BigDecimal("10000.00"), UUID.randomUUID(), clock.instant());
        when(paymentService.requireActiveAssignment(user.getId())).thenReturn(assignment);
        UUID proofId = paymentProofOwnedBy(user.getId(), DocType.PAYMENT_PROOF);
        when(investmentRepository.saveAndFlush(any(Investment.class))).thenAnswer(inv -> inv.getArgument(0));

        Investment inv = investmentService.submit(user.getId(), new InvestmentRequest(proofId), "idem-key");

        assertThat(inv.getAmount()).isEqualByComparingTo("10000.00");
        assertThat(inv.getPaymentAssignmentId()).isEqualTo(assignment.getId());
        assertThat(inv.getStatus()).isEqualTo(InvestmentStatus.PENDING_VERIFICATION);
    }

    @Test
    void submitBlocksSecondPendingProof() {
        User user = approvedUserWithBank();
        when(investmentRepository.existsByUserIdAndStatus(user.getId(),
                InvestmentStatus.PENDING_VERIFICATION)).thenReturn(true);

        assertThatThrownBy(() -> investmentService.submit(user.getId(),
                new InvestmentRequest(UUID.randomUUID()), "k"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void approveSetsRemainingPrincipalToAmount() {
        Investment inv = pendingInvestment();
        when(investmentRepository.findById(inv.getId())).thenReturn(Optional.of(inv));
        when(investmentRepository.save(any(Investment.class))).thenAnswer(i -> i.getArgument(0));

        investmentService.approve(inv.getId(), UUID.randomUUID());

        assertThat(inv.getRemainingPrincipal()).isEqualByComparingTo(inv.getAmount());
    }

    @Test
    void submitWithoutAssignmentFails() {
        User user = approvedUserWithBank();
        when(paymentService.requireActiveAssignment(user.getId()))
                .thenThrow(new ApiException(ErrorCode.NO_PAYMENT_ASSIGNMENT, "not assigned"));

        assertThatThrownBy(() -> investmentService.submit(user.getId(),
                new InvestmentRequest(UUID.randomUUID()), "k"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.NO_PAYMENT_ASSIGNMENT));
    }

    @Test
    void submitRejectsDocumentThatIsNotAPaymentProof() {
        User user = approvedUserWithBank();
        when(paymentService.requireActiveAssignment(user.getId())).thenReturn(new UserPaymentAssignment(
                user.getId(), UUID.randomUUID(), new BigDecimal("10000"), UUID.randomUUID(), clock.instant()));
        UUID panCard = paymentProofOwnedBy(user.getId(), DocType.PAN_CARD);

        assertThatThrownBy(() -> investmentService.submit(user.getId(), new InvestmentRequest(panCard), "k"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Payment proof");
    }

    @Test
    void submitRejectsNonApprovedUser() {
        User user = new User("9876543210", clock.instant());
        user.setStatus(UserStatus.KYC_SUBMITTED);
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> investmentService.submit(user.getId(),
                new InvestmentRequest(UUID.randomUUID()), "k"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void approvePostsPrincipalToLedger() {
        Investment inv = pendingInvestment();
        when(investmentRepository.findById(inv.getId())).thenReturn(Optional.of(inv));
        when(investmentRepository.save(any(Investment.class))).thenAnswer(i -> i.getArgument(0));

        investmentService.approve(inv.getId(), UUID.randomUUID());

        assertThat(inv.getStatus()).isEqualTo(InvestmentStatus.ACTIVE);
        Mockito.verify(ledgerPostingService).post(any(LedgerPostingService.Journal.class));
    }

    @Test
    void approveConsumesThePaymentAssignment() {
        Investment inv = pendingInvestment();
        when(investmentRepository.findById(inv.getId())).thenReturn(Optional.of(inv));
        when(investmentRepository.save(any(Investment.class))).thenAnswer(i -> i.getArgument(0));

        investmentService.approve(inv.getId(), UUID.randomUUID());

        Mockito.verify(paymentService).consumeAssignment(eq(inv.getPaymentAssignmentId()), any());
    }

    @Test
    void rejectKeepsThePaymentAssignmentActive() {
        Investment inv = pendingInvestment();
        when(investmentRepository.findById(inv.getId())).thenReturn(Optional.of(inv));
        when(investmentRepository.save(any(Investment.class))).thenAnswer(i -> i.getArgument(0));

        investmentService.reject(inv.getId(), UUID.randomUUID(), "Proof unclear");

        Mockito.verify(paymentService, Mockito.never()).consumeAssignment(any(), any());
    }

    @Test
    void approveNonPendingFails() {
        Investment inv = pendingInvestment();
        inv.approve(UUID.randomUUID(), clock.instant());
        when(investmentRepository.findById(inv.getId())).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> investmentService.approve(inv.getId(), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void rejectSetsReason() {
        Investment inv = pendingInvestment();
        when(investmentRepository.findById(inv.getId())).thenReturn(Optional.of(inv));
        when(investmentRepository.save(any(Investment.class))).thenAnswer(i -> i.getArgument(0));

        investmentService.reject(inv.getId(), UUID.randomUUID(), "Payment \"not\" received");

        assertThat(inv.getStatus()).isEqualTo(InvestmentStatus.REJECTED);
        assertThat(inv.getRejectionReason()).isEqualTo("Payment \"not\" received");
    }
}
