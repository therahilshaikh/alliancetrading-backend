package in.ruhmans.alliancetrading.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.files.repository.DocumentRepository;
import in.ruhmans.alliancetrading.files.service.FileService;
import in.ruhmans.alliancetrading.investment.domain.InvestmentStatus;
import in.ruhmans.alliancetrading.investment.repository.InvestmentRepository;
import in.ruhmans.alliancetrading.payment.domain.PaymentAccount;
import in.ruhmans.alliancetrading.payment.domain.PaymentAccountStatus;
import in.ruhmans.alliancetrading.payment.domain.UserPaymentAssignment;
import in.ruhmans.alliancetrading.payment.repository.PaymentAccountRepository;
import in.ruhmans.alliancetrading.payment.repository.UserPaymentAssignmentRepository;
import in.ruhmans.alliancetrading.payment.service.PaymentService;
import in.ruhmans.alliancetrading.payment.service.PaymentService.AssignmentCommand;
import in.ruhmans.alliancetrading.payment.service.PaymentService.NewAccount;
import in.ruhmans.alliancetrading.payment.service.UpiQrGenerator;
import in.ruhmans.alliancetrading.settings.service.SettingsService;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PaymentServiceTest {

    private PaymentAccountRepository accountRepository;
    private UserPaymentAssignmentRepository assignmentRepository;
    private UserRepository userRepository;
    private SettingsService settingsService;
    private InvestmentRepository investmentRepository;
    private PaymentService paymentService;
    private Clock clock;
    private final UUID admin = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accountRepository = Mockito.mock(PaymentAccountRepository.class);
        assignmentRepository = Mockito.mock(UserPaymentAssignmentRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        settingsService = Mockito.mock(SettingsService.class);
        clock = Clock.fixed(Instant.now(), ZoneId.of("Asia/Kolkata"));

        when(accountRepository.saveAndFlush(any(PaymentAccount.class))).thenAnswer(i -> i.getArgument(0));
        when(assignmentRepository.save(any(UserPaymentAssignment.class))).thenAnswer(i -> i.getArgument(0));
        when(settingsService.defaultInvestmentAmount()).thenReturn(Optional.of(new BigDecimal("10000.00")));

        investmentRepository = Mockito.mock(InvestmentRepository.class);
        paymentService = new PaymentService(accountRepository, assignmentRepository, userRepository,
                Mockito.mock(DocumentRepository.class), Mockito.mock(FileService.class), settingsService,
                new UpiQrGenerator(), Mockito.mock(AuditService.class), investmentRepository, clock);
    }

    private User userWithStatus(UserStatus status) {
        User user = new User("9876543210", clock.instant());
        user.setStatus(status);
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(assignmentRepository.findByUserIdAndActiveTrue(user.getId())).thenReturn(Optional.empty());
        return user;
    }

    private PaymentAccount savedAccount() {
        PaymentAccount account = new PaymentAccount("Ops", "ops@upi", "Alliance", null, admin, clock.instant());
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        return account;
    }

    @Test
    void assignFromSavedListUsesDefaultAmount() {
        User user = userWithStatus(UserStatus.KYC_SUBMITTED);
        PaymentAccount account = savedAccount();

        var assignment = paymentService.assign(user.getId(), admin,
                new AssignmentCommand(account.getId(), null, true, null));

        assertThat(assignment.getPaymentAccountId()).isEqualTo(account.getId());
        assertThat(assignment.getAmount()).isEqualByComparingTo("10000.00");
        assertThat(assignment.isActive()).isTrue();
    }

    @Test
    void newAccountSavedToListStaysActive() {
        User user = userWithStatus(UserStatus.KYC_SUBMITTED);

        paymentService.assign(user.getId(), admin, new AssignmentCommand(null,
                new NewAccount(null, "new.ops@upi", "Alliance Ops", null), true, new BigDecimal("25000")));

        var captor = ArgumentCaptor.forClass(PaymentAccount.class);
        verify(accountRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentAccountStatus.ACTIVE);
        assertThat(captor.getValue().getLabel()).isEqualTo("new.ops@upi");
    }

    @Test
    void newAccountNotSavedToListIsHiddenFromDropdown() {
        User user = userWithStatus(UserStatus.KYC_SUBMITTED);

        var assignment = paymentService.assign(user.getId(), admin, new AssignmentCommand(null,
                new NewAccount("One-off", "oneoff@upi", "Alliance", null), false, new BigDecimal("5000")));

        var captor = ArgumentCaptor.forClass(PaymentAccount.class);
        verify(accountRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentAccountStatus.ARCHIVED);
        assertThat(assignment.getAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void savingADuplicateUpiIdIsRejected() {
        User user = userWithStatus(UserStatus.KYC_SUBMITTED);
        when(accountRepository.existsActiveByVpa("ops@upi")).thenReturn(true);

        assertThatThrownBy(() -> paymentService.assign(user.getId(), admin, new AssignmentCommand(null,
                new NewAccount(null, "ops@upi", "Alliance", null), true, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already saved");
    }

    @Test
    void reassigningDeactivatesThePreviousAssignment() {
        User user = userWithStatus(UserStatus.APPROVED);
        PaymentAccount account = savedAccount();
        var previous = new UserPaymentAssignment(user.getId(), account.getId(), new BigDecimal("10000"),
                admin, clock.instant());
        when(assignmentRepository.findByUserIdAndActiveTrue(user.getId())).thenReturn(Optional.of(previous));

        paymentService.assign(user.getId(), admin, new AssignmentCommand(account.getId(), null, true,
                new BigDecimal("12000")));

        assertThat(previous.isActive()).isFalse();
        assertThat(previous.getDeactivatedAt()).isNotNull();
    }

    @Test
    void mustPickExactlyOneSource() {
        User user = userWithStatus(UserStatus.KYC_SUBMITTED);

        assertThatThrownBy(() -> paymentService.assign(user.getId(), admin,
                new AssignmentCommand(null, null, true, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("either");
        assertThatThrownBy(() -> paymentService.assign(user.getId(), admin,
                new AssignmentCommand(UUID.randomUUID(), new NewAccount(null, "a@upi", "A", null), true, null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void archivedAccountCannotBePicked() {
        User user = userWithStatus(UserStatus.KYC_SUBMITTED);
        PaymentAccount account = savedAccount();
        account.archive(admin, clock.instant());

        assertThatThrownBy(() -> paymentService.assign(user.getId(), admin,
                new AssignmentCommand(account.getId(), null, true, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("archived");
    }

    @Test
    void cannotAssignToNewlyRegisteredUser() {
        User user = userWithStatus(UserStatus.REGISTERED);

        assertThatThrownBy(() -> paymentService.assign(user.getId(), admin,
                new AssignmentCommand(UUID.randomUUID(), null, true, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void instructionsCarryAmountAndUpiLink() {
        PaymentAccount account = savedAccount();
        UUID userId = UUID.randomUUID();
        var assignment = new UserPaymentAssignment(userId, account.getId(), new BigDecimal("10000.00"),
                admin, clock.instant());
        when(assignmentRepository.findByUserIdAndActiveTrue(userId)).thenReturn(Optional.of(assignment));

        var instructions = paymentService.instructionsFor(userId);

        assertThat(instructions.vpa()).isEqualTo("ops@upi");
        assertThat(instructions.amount()).isEqualByComparingTo("10000.00");
        assertThat(instructions.upiUri()).startsWith("upi://pay?pa=ops@upi&pn=Alliance&am=10000.00");
    }

    @Test
    void instructionsWithoutAssignmentFail() {
        UUID userId = UUID.randomUUID();
        when(assignmentRepository.findByUserIdAndActiveTrue(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.instructionsFor(userId))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.NO_PAYMENT_ASSIGNMENT));
    }

    @Test
    void expectedPaymentsAreResolvedInBatchedLookups() {
        PaymentAccount account = new PaymentAccount("Ops", "ops@upi", "Alliance", null, admin, clock.instant());
        var a1 = new UserPaymentAssignment(UUID.randomUUID(), account.getId(), new BigDecimal("10000.00"), admin, clock.instant());
        var a2 = new UserPaymentAssignment(UUID.randomUUID(), account.getId(), new BigDecimal("25000.00"), admin, clock.instant());
        when(assignmentRepository.findAllById(any())).thenReturn(java.util.List.of(a1, a2));
        when(accountRepository.findAllById(any())).thenReturn(java.util.List.of(account));

        var expected = paymentService.expectedByAssignmentId(java.util.Set.of(a1.getId(), a2.getId()));

        assertThat(expected.get(a1.getId())).isEqualTo(new PaymentService.ExpectedPayment("ops@upi", new BigDecimal("10000.00")));
        assertThat(expected.get(a2.getId()).amount()).isEqualByComparingTo("25000.00");
        verify(assignmentRepository, Mockito.times(1)).findAllById(any());
        verify(accountRepository, Mockito.times(1)).findAllById(any());
    }

    @Test
    void expectedPaymentsForNoIdsSkipTheDatabase() {
        assertThat(paymentService.expectedByAssignmentId(java.util.Set.of())).isEmpty();
        verify(assignmentRepository, never()).findAllById(any());
    }

    // ---- Default payment account ----

    @Test
    void archivingTheDefaultAccountIsBlocked() {
        PaymentAccount account = savedAccount();
        when(settingsService.isDefaultPaymentAccount(account.getId())).thenReturn(true);

        assertThatThrownBy(() -> paymentService.archiveAccount(account.getId(), admin))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.PAYMENT_ACCOUNT_IN_USE));
        assertThat(account.isActive()).isTrue();
    }

    @Test
    void archivingAnotherAccountWorks() {
        PaymentAccount account = savedAccount();
        when(settingsService.isDefaultPaymentAccount(account.getId())).thenReturn(false);

        paymentService.archiveAccount(account.getId(), admin);

        assertThat(account.getStatus()).isEqualTo(PaymentAccountStatus.ARCHIVED);
    }

    @Test
    void defaultAccountNeedsExactlyOneSource() {
        PaymentAccount account = savedAccount();
        var newAccount = new NewAccount(null, "new@upi", "Alliance", null);

        assertThatThrownBy(() -> paymentService.resolveDefaultAccount(admin, null, null))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThatThrownBy(() -> paymentService.resolveDefaultAccount(admin, account.getId(), newAccount))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void archivedAccountCannotBeTheDefault() {
        PaymentAccount account = savedAccount();
        account.archive(admin, clock.instant());

        assertThatThrownBy(() -> paymentService.resolveDefaultAccount(admin, account.getId(), null))
                .hasMessageContaining("archived");
    }

    @Test
    void newDefaultAccountIsAlwaysSavedToTheList() {
        PaymentAccount created = paymentService.resolveDefaultAccount(admin, null,
                new NewAccount(null, "new@upi", "Alliance", null));

        assertThat(created.isActive()).isTrue();
        assertThat(created.getVpa()).isEqualTo("new@upi");
    }
    // ---- Cancel assignment ----

    @Test
    void cancelAssignmentDeactivatesTheActiveRow() {
        UserPaymentAssignment assignment = new UserPaymentAssignment(UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("20000.00"), admin, clock.instant());
        when(assignmentRepository.findByUserIdAndActiveTrue(assignment.getUserId()))
                .thenReturn(Optional.of(assignment));

        paymentService.cancelAssignment(assignment.getUserId(), admin);

        assertThat(assignment.isActive()).isFalse();
        assertThat(assignment.getDeactivatedAt()).isNotNull();
    }

    @Test
    void cancelAssignmentWithoutAnActiveOneFails() {
        UUID userId = UUID.randomUUID();
        when(assignmentRepository.findByUserIdAndActiveTrue(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.cancelAssignment(userId, admin))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.NO_PAYMENT_ASSIGNMENT));
    }

    @Test
    void consumeAssignmentDeactivatesOnce() {
        UserPaymentAssignment assignment = new UserPaymentAssignment(UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("50000.00"), admin, clock.instant());
        when(assignmentRepository.findById(assignment.getId())).thenReturn(Optional.of(assignment));

        paymentService.consumeAssignment(assignment.getId(), admin);
        paymentService.consumeAssignment(assignment.getId(), admin);

        assertThat(assignment.isActive()).isFalse();
    }

    @Test
    void cancelAssignmentBlockedWhileProofUnderReview() {
        UUID userId = UUID.randomUUID();
        when(investmentRepository.existsByUserIdAndStatus(userId,
                InvestmentStatus.PENDING_VERIFICATION)).thenReturn(true);

        assertThatThrownBy(() -> paymentService.cancelAssignment(userId, admin))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("ILLEGAL_STATE_TRANSITION"));
    }
}

