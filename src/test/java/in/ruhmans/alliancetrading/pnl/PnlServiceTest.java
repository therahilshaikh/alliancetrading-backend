package in.ruhmans.alliancetrading.pnl;

import in.ruhmans.alliancetrading.user.service.UserDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.admin.domain.AdminRole;
import in.ruhmans.alliancetrading.admin.domain.AdminUser;
import in.ruhmans.alliancetrading.admin.repository.AdminUserRepository;
import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.files.repository.DocumentRepository;
import in.ruhmans.alliancetrading.investment.domain.Investment;
import in.ruhmans.alliancetrading.investment.domain.InvestmentStatus;
import in.ruhmans.alliancetrading.investment.repository.InvestmentRepository;
import in.ruhmans.alliancetrading.ledger.domain.Bucket;
import in.ruhmans.alliancetrading.ledger.domain.UserBalance;
import in.ruhmans.alliancetrading.ledger.repository.UserBalanceRepository;
import in.ruhmans.alliancetrading.ledger.service.LedgerPostingService;
import in.ruhmans.alliancetrading.notification.service.OutboxService;
import in.ruhmans.alliancetrading.pnl.domain.PnlBatch;
import in.ruhmans.alliancetrading.pnl.domain.PnlBatchStatus;
import in.ruhmans.alliancetrading.pnl.domain.PnlBatchType;
import in.ruhmans.alliancetrading.pnl.domain.PnlLine;
import in.ruhmans.alliancetrading.pnl.domain.PnlTargetType;
import in.ruhmans.alliancetrading.pnl.domain.PnlValueType;
import in.ruhmans.alliancetrading.pnl.repository.PnlAllocationRepository;
import in.ruhmans.alliancetrading.pnl.repository.PnlBatchRepository;
import in.ruhmans.alliancetrading.pnl.repository.PnlLineRepository;
import in.ruhmans.alliancetrading.pnl.service.PnlService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PnlServiceTest {

    private PnlBatchRepository batchRepository;
    private PnlLineRepository lineRepository;
    private InvestmentRepository investmentRepository;
    private UserBalanceRepository userBalanceRepository;
    private AdminUserRepository adminUserRepository;
    private PnlService pnlService;
    private Clock clock;

    private UUID maker;
    private UUID checker;

    @BeforeEach
    void setUp() {
        batchRepository = Mockito.mock(PnlBatchRepository.class);
        lineRepository = Mockito.mock(PnlLineRepository.class);
        investmentRepository = Mockito.mock(InvestmentRepository.class);
        userBalanceRepository = Mockito.mock(UserBalanceRepository.class);
        adminUserRepository = Mockito.mock(AdminUserRepository.class);
        clock = Clock.fixed(Instant.now(), ZoneId.of("Asia/Kolkata"));

        pnlService = new PnlService(batchRepository, lineRepository, Mockito.mock(PnlAllocationRepository.class),
                investmentRepository, userBalanceRepository,
                Mockito.mock(DocumentRepository.class), adminUserRepository,
                Mockito.mock(LedgerPostingService.class), Mockito.mock(OutboxService.class),
                Mockito.mock(AuditService.class), Mockito.mock(UserDirectory.class), clock);

        maker = admin("maker@x.in").getId();
        checker = admin("checker@x.in").getId();
    }

    private AdminUser admin(String email) {
        AdminUser admin = new AdminUser(email, "hash", AdminRole.ADMIN, "Admin", clock.instant());
        when(adminUserRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        return admin;
    }

    private Investment activeInvestment(UUID userId, BigDecimal amount) {
        Investment inv = new Investment(userId, amount, UUID.randomUUID(), UUID.randomUUID(), clock.instant());
        inv.approve(UUID.randomUUID(), clock.instant());
        return inv;
    }

    private PnlService.CreateBatchCommand command(BigDecimal controlTotal, UUID approver,
                                                  List<PnlService.LineInput> lines) {
        return new PnlService.CreateBatchCommand(PnlBatchType.REGULAR, PnlValueType.FIXED, LocalDate.now(), null,
                controlTotal, null, null, approver, lines);
    }

    private PnlBatch draftAssignedTo(UUID assignee) {
        PnlBatch batch = new PnlBatch(PnlBatchType.REGULAR, PnlValueType.FIXED, LocalDate.now(), null,
                new BigDecimal("100"), null, maker, assignee, null, clock.instant());
        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
        return batch;
    }

    @Test
    void createBatchValidatesControlTotal() {
        Investment inv = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        when(investmentRepository.findById(inv.getId())).thenReturn(Optional.of(inv));
        var lines = List.of(new PnlService.LineInput(PnlTargetType.INVESTMENT, inv.getId(), new BigDecimal("100.00"), null));

        assertThatThrownBy(() -> pnlService.createBatch(maker, command(new BigDecimal("999.00"), checker, lines)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("control total");
    }

    @Test
    void createBatchRequiresNonEmptyLines() {
        assertThatThrownBy(() -> pnlService.createBatch(maker, command(BigDecimal.ZERO, checker, List.of())))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void createBatchRequiresAnApprover() {
        var lines = List.of(new PnlService.LineInput(PnlTargetType.INVESTMENT, UUID.randomUUID(), new BigDecimal("100.00"), null));

        assertThatThrownBy(() -> pnlService.createBatch(maker, command(new BigDecimal("100.00"), null, lines)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_APPROVER));
    }

    @Test
    void makerCannotPickThemselvesAsApprover() {
        var lines = List.of(new PnlService.LineInput(PnlTargetType.INVESTMENT, UUID.randomUUID(), new BigDecimal("100.00"), null));

        assertThatThrownBy(() -> pnlService.createBatch(maker, command(new BigDecimal("100.00"), maker, lines)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_APPROVER));
    }

    @Test
    void disabledAdminCannotBeApprover() {
        AdminUser disabled = admin("left@x.in");
        disabled.setStatus("DISABLED");
        var lines = List.of(new PnlService.LineInput(PnlTargetType.INVESTMENT, UUID.randomUUID(), new BigDecimal("100.00"), null));

        assertThatThrownBy(() -> pnlService.createBatch(maker,
                command(new BigDecimal("100.00"), disabled.getId(), lines)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_APPROVER));
    }

    @Test
    void onlyAssignedApproverCanApprove() {
        PnlBatch batch = draftAssignedTo(checker);
        UUID someoneElse = UUID.randomUUID();

        assertThatThrownBy(() -> pnlService.approve(batch.getId(), someoneElse))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.MAKER_CHECKER_VIOLATION))
                .hasMessageContaining("assigned to another admin");
    }

    @Test
    void onlyAssignedApproverCanReject() {
        PnlBatch batch = draftAssignedTo(checker);

        assertThatThrownBy(() -> pnlService.reject(batch.getId(), UUID.randomUUID(), "wrong figures"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.MAKER_CHECKER_VIOLATION));
    }

    @Test
    void assignedApproverCanReject() {
        PnlBatch batch = draftAssignedTo(checker);

        pnlService.reject(batch.getId(), checker, "wrong figures");

        assertThat(batch.getStatus()).isEqualTo(PnlBatchStatus.REJECTED);
        assertThat(batch.getCheckerId()).isEqualTo(checker);
    }

    @Test
    void approveByMakerRejected() {
        PnlBatch batch = draftAssignedTo(checker);

        assertThatThrownBy(() -> pnlService.approve(batch.getId(), maker))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("MAKER_CHECKER_VIOLATION"));
    }

    @Test
    void approveNonDraftRejected() {
        PnlBatch batch = draftAssignedTo(checker);
        batch.approve(checker, clock.instant());

        assertThatThrownBy(() -> pnlService.approve(batch.getId(), checker))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void lossFloorBreachRejectedOnCreate() {
        UUID userId = UUID.randomUUID();
        Investment inv = activeInvestment(userId, new BigDecimal("50000"));

        UserBalance balance = new UserBalance(userId, clock.instant());
        balance.apply(Bucket.PRINCIPAL, new BigDecimal("50000"), clock.instant());
        // profit is 0; delta = -60000 would breach the floor (50000 + 0 - 60000 < 0)

        when(investmentRepository.findById(inv.getId())).thenReturn(Optional.of(inv));
        when(userBalanceRepository.findById(userId)).thenReturn(Optional.of(balance));
        when(batchRepository.save(any(PnlBatch.class))).thenAnswer(i -> i.getArgument(0));
        when(lineRepository.findByBatchId(any())).thenReturn(List.of(
                new PnlLine(UUID.randomUUID(), PnlTargetType.INVESTMENT, inv.getId(), new BigDecimal("-60000"), null, new BigDecimal("-60000"))));

        var lines = List.of(new PnlService.LineInput(PnlTargetType.INVESTMENT, inv.getId(), new BigDecimal("-60000"), null));

        assertThatThrownBy(() -> pnlService.createBatch(maker, command(new BigDecimal("-60000"), checker, lines)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Loss floor");
    }

    // ---- Fixed batches ----

    private static PnlService.LineInput allEach(String amount) {
        return new PnlService.LineInput(PnlTargetType.ALL, null, new BigDecimal(amount), null);
    }

    @Test
    void fixedAmountGoesToEveryActiveInvestmentAndTheServerComputesTheTotal() {
        Investment a = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        Investment b = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        Investment c = activeInvestment(UUID.randomUUID(), new BigDecimal("50000"));
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE)).thenReturn(List.of(a, b, c));
        List<PnlLine> lines = storeBatchesAndLines();

        PnlBatch batch = pnlService.createBatch(maker, command(null, checker, List.of(allEach("200"))));

        assertThat(batch.getValueType()).isEqualTo(PnlValueType.FIXED);
        assertThat(batch.getControlTotal()).isEqualByComparingTo("600.00");
        assertThat(lines.getFirst().getAmountPerInvestment()).isEqualByComparingTo("200.00");
        assertThat(lines.getFirst().getAmount()).isEqualByComparingTo("600.00");
        assertThat(pnlService.preview(batch.getId()).allocations()).extracting(PnlService.AllocationPreview::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsOnly(new BigDecimal("200.00"));
    }

    @Test
    void fixedBatchWithMatchingControlTotalIsAccepted() {
        Investment a = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        Investment b = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE)).thenReturn(List.of(a, b));
        storeBatchesAndLines();

        PnlBatch batch = pnlService.createBatch(maker, command(new BigDecimal("400.00"), checker, List.of(allEach("200"))));

        assertThat(batch.getControlTotal()).isEqualByComparingTo("400.00");
    }

    @Test
    void zeroFixedAmountRejected() {
        assertThatThrownBy(() -> pnlService.createBatch(maker, command(null, checker, List.of(allEach("0")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("non-zero");
    }

    @Test
    void valueTypeIsRequired() {
        var noType = new PnlService.CreateBatchCommand(PnlBatchType.REGULAR, null, LocalDate.now(), null,
                null, null, null, checker, List.of(allEach("200")));

        assertThatThrownBy(() -> pnlService.createBatch(maker, noType))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("FIXED or PERCENTAGE");
    }

    @Test
    void fixedApproveRefusesWhenActiveInvestmentsChanged() {
        Investment first = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        Investment joinedLater = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE))
                .thenReturn(List.of(first))
                .thenReturn(List.of(first)) // create-time validation
                .thenReturn(List.of(first, joinedLater));
        storeBatchesAndLines();

        PnlBatch batch = pnlService.createBatch(maker, command(null, checker, List.of(allEach("200"))));

        assertThatThrownBy(() -> pnlService.approve(batch.getId(), checker))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION))
                .hasMessageContaining("200.00 each");
    }

    // ---- Dry-run preview ----

    @Test
    void dryRunPreviewComputesAmountsWithoutSavingOrAnApprover() {
        Investment small = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        Investment large = activeInvestment(UUID.randomUUID(), new BigDecimal("50000"));
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE)).thenReturn(List.of(small, large));
        when(userBalanceRepository.findById(any())).thenReturn(Optional.empty());

        var draft = new PnlService.CreateBatchCommand(PnlBatchType.REGULAR, PnlValueType.PERCENTAGE, LocalDate.now(),
                null, null, null, null, null, List.of(allAt("5")));
        var preview = pnlService.previewDraft(maker, draft);

        assertThat(preview.batchId()).isNull();
        assertThat(preview.controlTotal()).isEqualByComparingTo("3000.00");
        assertThat(preview.allocations()).extracting(PnlService.AllocationPreview::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("500.00"), new BigDecimal("2500.00"));
        Mockito.verify(batchRepository, Mockito.never()).save(any());
        Mockito.verify(lineRepository, Mockito.never()).save(any());
    }

    @Test
    void dryRunPreviewReportsTheLossFloor() {
        UUID userId = UUID.randomUUID();
        Investment inv = activeInvestment(userId, new BigDecimal("10000"));
        when(investmentRepository.findById(inv.getId())).thenReturn(Optional.of(inv));
        when(userBalanceRepository.findById(userId)).thenReturn(Optional.empty());

        var draft = new PnlService.CreateBatchCommand(PnlBatchType.REGULAR, PnlValueType.FIXED, LocalDate.now(),
                null, null, null, null, null,
                List.of(new PnlService.LineInput(PnlTargetType.INVESTMENT, inv.getId(), new BigDecimal("-500"), null)));

        assertThatThrownBy(() -> pnlService.previewDraft(maker, draft))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Loss floor");
    }

    // ---- Percentage batches ----

    /** Persists saved batches and lines in memory so create -> preview -> approve can run. */
    private List<PnlLine> storeBatchesAndLines() {
        List<PnlLine> saved = new java.util.ArrayList<>();
        final PnlBatch[] savedBatch = new PnlBatch[1];
        when(batchRepository.save(any(PnlBatch.class))).thenAnswer(i -> savedBatch[0] = i.getArgument(0));
        when(batchRepository.findById(any())).thenAnswer(i -> Optional.ofNullable(savedBatch[0]));
        when(lineRepository.save(any(PnlLine.class))).thenAnswer(i -> {
            saved.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(lineRepository.findByBatchId(any())).thenAnswer(i -> List.copyOf(saved));
        when(userBalanceRepository.findById(any())).thenReturn(Optional.empty());
        return saved;
    }

    private PnlService.CreateBatchCommand percentageCommand(BigDecimal controlTotal, List<PnlService.LineInput> lines) {
        return new PnlService.CreateBatchCommand(PnlBatchType.REGULAR, PnlValueType.PERCENTAGE, LocalDate.now(),
                null, controlTotal, null, null, checker, lines);
    }

    private static PnlService.LineInput allAt(String percentage) {
        return new PnlService.LineInput(PnlTargetType.ALL, null, null, new BigDecimal(percentage));
    }

    @Test
    void percentageOfPrincipalAppliesToEveryActiveInvestment() {
        Investment small = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        Investment large = activeInvestment(UUID.randomUUID(), new BigDecimal("50000"));
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE))
                .thenReturn(List.of(small, large));
        List<PnlLine> lines = storeBatchesAndLines();

        PnlBatch batch = pnlService.createBatch(maker, percentageCommand(null, List.of(allAt("1"))));

        assertThat(batch.getValueType()).isEqualTo(PnlValueType.PERCENTAGE);
        assertThat(batch.getControlTotal()).isEqualByComparingTo("600.00");
        assertThat(lines.get(0).getPercentage()).isEqualByComparingTo("1");
        assertThat(lines.get(0).getAmount()).isEqualByComparingTo("600.00");

        var preview = pnlService.preview(batch.getId());
        assertThat(preview.warnings()).isEmpty();
        assertThat(preview.allocations()).extracting(PnlService.AllocationPreview::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("100.00"), new BigDecimal("500.00"));
    }

    @Test
    void negativePercentageRoundsEachInvestmentToThePaisa() {
        UUID userId = UUID.randomUUID();
        Investment inv = activeInvestment(userId, new BigDecimal("10001"));
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE)).thenReturn(List.of(inv));
        storeBatchesAndLines();
        UserBalance balance = new UserBalance(userId, clock.instant());
        balance.apply(Bucket.PRINCIPAL, new BigDecimal("10001"), clock.instant());
        when(userBalanceRepository.findById(userId)).thenReturn(Optional.of(balance));

        PnlBatch batch = pnlService.createBatch(maker, percentageCommand(null, List.of(allAt("-0.333"))));

        // 10001 x -0.333% = -33.30333 -> -33.30
        assertThat(batch.getControlTotal()).isEqualByComparingTo("-33.30");
    }

    @Test
    void suppliedControlTotalMustMatchComputedTotal() {
        Investment inv = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE)).thenReturn(List.of(inv));
        storeBatchesAndLines();

        assertThatThrownBy(() -> pnlService.createBatch(maker,
                percentageCommand(new BigDecimal("999.00"), List.of(allAt("1")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("control total");
    }

    @Test
    void percentageLineRequiresAPercentage() {
        var line = new PnlService.LineInput(PnlTargetType.ALL, null, null, null);

        assertThatThrownBy(() -> pnlService.createBatch(maker, percentageCommand(null, List.of(line))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("percentage is required");
    }

    @Test
    void percentageOutOfRangeRejected() {
        assertThatThrownBy(() -> pnlService.createBatch(maker, percentageCommand(null, List.of(allAt("-100")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("greater than -100");
    }

    @Test
    void amountBatchRejectsPercentageLines() {
        var line = new PnlService.LineInput(PnlTargetType.INVESTMENT, UUID.randomUUID(),
                new BigDecimal("100.00"), new BigDecimal("1"));

        assertThatThrownBy(() -> pnlService.createBatch(maker, command(new BigDecimal("100.00"), checker, List.of(line))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("only allowed when valueType is PERCENTAGE");
    }

    @Test
    void approveRefusesWhenActiveInvestmentsChanged() {
        Investment first = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        Investment joinedLater = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE))
                .thenReturn(List.of(first))
                .thenReturn(List.of(first)) // create-time validation
                .thenReturn(List.of(first, joinedLater));
        storeBatchesAndLines();

        PnlBatch batch = pnlService.createBatch(maker, percentageCommand(null, List.of(allAt("1"))));

        assertThatThrownBy(() -> pnlService.approve(batch.getId(), checker))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION))
                .hasMessageContaining("active investments changed");
    }

    // ---- largeChange flag ----

    /** Returns the saved-batch preview and the dry-run preview for the same percentage across two members. */
    private List<PnlService.PreviewResult> bothPreviews(String percentage) {
        Investment a = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        Investment b = activeInvestment(UUID.randomUUID(), new BigDecimal("50000"));
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE)).thenReturn(List.of(a, b));
        storeBatchesAndLines();
        PnlBatch batch = pnlService.createBatch(maker, percentageCommand(null, List.of(allAt(percentage))));
        return List.of(pnlService.preview(batch.getId()),
                pnlService.previewDraft(maker, percentageCommand(null, List.of(allAt(percentage)))));
    }

    @Test
    void smallChangeIsNotFlaggedAndHasNoWarnings() {
        for (var preview : bothPreviews("1")) {
            assertThat(preview.allocations()).extracting(PnlService.AllocationPreview::largeChange).containsOnly(false);
            assertThat(preview.warnings()).isEmpty();
            assertThat(preview.largeChangeThresholdPct()).isEqualByComparingTo("10");
        }
    }

    @Test
    void largeChangeIsFlaggedPerRowWithoutPerMemberWarnings() {
        for (var preview : bothPreviews("12")) {
            assertThat(preview.allocations()).extracting(PnlService.AllocationPreview::largeChange).containsOnly(true);
            assertThat(preview.warnings()).isEmpty();
        }
    }

    @Test
    void exactlyTenPercentIsNotALargeChange() {
        for (var preview : bothPreviews("10")) {
            assertThat(preview.allocations()).extracting(PnlService.AllocationPreview::largeChange).containsOnly(false);
        }
    }

    @Test
    void savedPreviewReportsDriftAsABatchWarning() {
        Investment first = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        Investment joinedLater = activeInvestment(UUID.randomUUID(), new BigDecimal("10000"));
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE))
                .thenReturn(List.of(first))
                .thenReturn(List.of(first)) // create-time validation
                .thenReturn(List.of(first, joinedLater));
        storeBatchesAndLines();
        PnlBatch batch = pnlService.createBatch(maker, percentageCommand(null, List.of(allAt("1"))));

        assertThat(pnlService.preview(batch.getId()).warnings())
                .singleElement().asString().contains("active investments changed");
    }
}
