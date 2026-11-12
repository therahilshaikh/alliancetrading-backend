package in.ruhmans.alliancetrading.pnl;

import in.ruhmans.alliancetrading.user.service.UserDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
import in.ruhmans.alliancetrading.pnl.domain.PnlAllocation;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PnlServiceTest {

    private PnlBatchRepository batchRepository;
    private PnlLineRepository lineRepository;
    private PnlAllocationRepository allocationRepository;
    private InvestmentRepository investmentRepository;
    private UserBalanceRepository userBalanceRepository;
    private AdminUserRepository adminUserRepository;
    private LedgerPostingService ledgerPostingService;
    private OutboxService outboxService;
    private PnlService pnlService;
    private Clock clock;

    private UUID maker;
    private UUID checker;

    @BeforeEach
    void setUp() {
        batchRepository = Mockito.mock(PnlBatchRepository.class);
        lineRepository = Mockito.mock(PnlLineRepository.class);
        allocationRepository = Mockito.mock(PnlAllocationRepository.class);
        investmentRepository = Mockito.mock(InvestmentRepository.class);
        userBalanceRepository = Mockito.mock(UserBalanceRepository.class);
        adminUserRepository = Mockito.mock(AdminUserRepository.class);
        ledgerPostingService = Mockito.mock(LedgerPostingService.class);
        outboxService = Mockito.mock(OutboxService.class);
        clock = Clock.fixed(Instant.now(), ZoneId.of("Asia/Kolkata"));

        pnlService = new PnlService(batchRepository, lineRepository, allocationRepository,
                investmentRepository, userBalanceRepository,
                Mockito.mock(DocumentRepository.class), adminUserRepository,
                ledgerPostingService, outboxService,
                Mockito.mock(AuditService.class), Mockito.mock(UserDirectory.class), clock);

        maker = admin("maker@x.in").getId();
        checker = admin("checker@x.in").getId();
    }

    // ---- Fixtures ----

    private AdminUser admin(String email) {
        AdminUser admin = new AdminUser(email, "hash", AdminRole.ADMIN, "Admin", clock.instant());
        when(adminUserRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        return admin;
    }

    private Investment activeInvestment(UUID userId, String amount) {
        Investment inv = new Investment(userId, new BigDecimal(amount), UUID.randomUUID(), UUID.randomUUID(),
                clock.instant());
        inv.approve(UUID.randomUUID(), clock.instant());
        return inv;
    }

    /**
     * Registers the ACTIVE investments, oldest first, for both ALL and MEMBER lookups, and a ledger balance per
     * member whose principal matches their investments (nothing on hold). Override with {@link #balance}.
     */
    private void activeInvestments(Investment... investments) {
        List<Investment> all = List.of(investments);
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE)).thenReturn(all);
        all.stream().map(Investment::getUserId).distinct().forEach(userId -> {
            List<Investment> own = all.stream().filter(i -> i.getUserId().equals(userId)).toList();
            when(investmentRepository.findActiveByUserIdOldestFirst(userId)).thenReturn(own);
            balance(userId, own.stream().map(Investment::getRemainingPrincipal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).toPlainString(), "0");
        });
    }

    private void balance(UUID userId, String principal, String profit) {
        UserBalance balance = new UserBalance(userId, clock.instant());
        balance.apply(Bucket.PRINCIPAL, new BigDecimal(principal), clock.instant());
        if (new BigDecimal(profit).signum() != 0) {
            balance.apply(Bucket.PROFIT, new BigDecimal(profit), clock.instant());
        }
        when(userBalanceRepository.findById(userId)).thenReturn(Optional.of(balance));
    }

    /** Persists saved batches and lines in memory so create -> preview -> approve can run. */
    private List<PnlLine> storeBatchesAndLines() {
        List<PnlLine> saved = new ArrayList<>();
        final PnlBatch[] savedBatch = new PnlBatch[1];
        when(batchRepository.save(any(PnlBatch.class))).thenAnswer(i -> savedBatch[0] = i.getArgument(0));
        when(batchRepository.findById(any())).thenAnswer(i -> Optional.ofNullable(savedBatch[0]));
        when(lineRepository.save(any(PnlLine.class))).thenAnswer(i -> {
            saved.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(lineRepository.findByBatchId(any())).thenAnswer(i -> List.copyOf(saved));
        return saved;
    }

    private PnlService.CreateBatchCommand fixed(BigDecimal controlTotal, UUID approver,
                                                List<PnlService.LineInput> lines) {
        return new PnlService.CreateBatchCommand(PnlBatchType.REGULAR, PnlValueType.FIXED, LocalDate.now(), null,
                controlTotal, null, null, approver, lines);
    }

    private PnlService.CreateBatchCommand percentage(BigDecimal controlTotal, List<PnlService.LineInput> lines) {
        return new PnlService.CreateBatchCommand(PnlBatchType.REGULAR, PnlValueType.PERCENTAGE, LocalDate.now(),
                null, controlTotal, null, null, checker, lines);
    }

    private static PnlService.LineInput allEach(String amount) {
        return new PnlService.LineInput(PnlTargetType.ALL, null, new BigDecimal(amount), null);
    }

    private static PnlService.LineInput memberEach(UUID userId, String amount) {
        return new PnlService.LineInput(PnlTargetType.MEMBER, userId, new BigDecimal(amount), null);
    }

    private static PnlService.LineInput allAt(String pct) {
        return new PnlService.LineInput(PnlTargetType.ALL, null, null, new BigDecimal(pct));
    }

    private PnlBatch draftAssignedTo(UUID assignee) {
        PnlBatch batch = new PnlBatch(PnlBatchType.REGULAR, PnlValueType.FIXED, LocalDate.now(), null,
                new BigDecimal("100"), null, maker, assignee, null, clock.instant());
        when(batchRepository.findById(batch.getId())).thenReturn(Optional.of(batch));
        return batch;
    }

    private static PnlService.AllocationPreview rowFor(PnlService.PreviewResult preview, UUID userId) {
        return preview.allocations().stream().filter(a -> a.userId().equals(userId)).findFirst().orElseThrow();
    }

    // ---- Validation and maker-checker ----

    @Test
    void createBatchValidatesControlTotal() {
        UUID userId = UUID.randomUUID();
        activeInvestments(activeInvestment(userId, "10000"));

        assertThatThrownBy(() -> pnlService.createBatch(maker,
                fixed(new BigDecimal("999.00"), checker, List.of(memberEach(userId, "100")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("control total");
    }

    @Test
    void createBatchRequiresNonEmptyLines() {
        assertThatThrownBy(() -> pnlService.createBatch(maker, fixed(BigDecimal.ZERO, checker, List.of())))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void createBatchRequiresAnApprover() {
        assertThatThrownBy(() -> pnlService.createBatch(maker,
                fixed(null, null, List.of(memberEach(UUID.randomUUID(), "100")))))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_APPROVER));
    }

    @Test
    void makerCannotPickThemselvesAsApprover() {
        assertThatThrownBy(() -> pnlService.createBatch(maker,
                fixed(null, maker, List.of(memberEach(UUID.randomUUID(), "100")))))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_APPROVER));
    }

    @Test
    void disabledAdminCannotBeApprover() {
        AdminUser disabled = admin("left@x.in");
        disabled.setStatus("DISABLED");

        assertThatThrownBy(() -> pnlService.createBatch(maker,
                fixed(null, disabled.getId(), List.of(memberEach(UUID.randomUUID(), "100")))))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_APPROVER));
    }

    @Test
    void onlyAssignedApproverCanApprove() {
        PnlBatch batch = draftAssignedTo(checker);

        assertThatThrownBy(() -> pnlService.approve(batch.getId(), UUID.randomUUID()))
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
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.MAKER_CHECKER_VIOLATION));
    }

    @Test
    void approveNonDraftRejected() {
        PnlBatch batch = draftAssignedTo(checker);
        batch.approve(checker, clock.instant());

        assertThatThrownBy(() -> pnlService.approve(batch.getId(), checker))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
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
    void zeroFixedAmountRejected() {
        assertThatThrownBy(() -> pnlService.createBatch(maker, fixed(null, checker, List.of(allEach("0")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("non-zero");
    }

    @Test
    void fixedBatchRejectsPercentageLines() {
        var line = new PnlService.LineInput(PnlTargetType.MEMBER, UUID.randomUUID(),
                new BigDecimal("100.00"), new BigDecimal("1"));

        assertThatThrownBy(() -> pnlService.createBatch(maker, fixed(null, checker, List.of(line))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("only allowed when valueType is PERCENTAGE");
    }

    @Test
    void memberLineRequiresUserId() {
        var line = new PnlService.LineInput(PnlTargetType.MEMBER, null, new BigDecimal("100"), null);

        assertThatThrownBy(() -> pnlService.createBatch(maker, fixed(null, checker, List.of(line))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("userId is required");
    }

    @Test
    void memberWithoutActiveInvestmentRejected() {
        UUID userId = UUID.randomUUID();
        when(investmentRepository.findActiveByUserIdOldestFirst(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> pnlService.createBatch(maker, fixed(null, checker, List.of(memberEach(userId, "100")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no ACTIVE investment");
    }

    @Test
    void memberTargetedTwiceRejected() {
        UUID userId = UUID.randomUUID();
        activeInvestments(activeInvestment(userId, "10000"));
        storeBatchesAndLines();

        assertThatThrownBy(() -> pnlService.createBatch(maker,
                fixed(null, checker, List.of(allEach("100"), memberEach(userId, "50")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("more than one line");
    }

    // ---- FIXED: per member, however many investments ----

    @Test
    void fixedAmountIsPerMemberAndSplitAcrossTheirInvestments() {
        UUID ananya = UUID.randomUUID();
        UUID ravi = UUID.randomUUID();
        Investment first = activeInvestment(ananya, "50000");
        Investment raviOnly = activeInvestment(ravi, "10000");
        Investment topUp = activeInvestment(ananya, "20000");
        activeInvestments(first, raviOnly, topUp);
        List<PnlLine> lines = storeBatchesAndLines();

        PnlBatch batch = pnlService.createBatch(maker, fixed(null, checker, List.of(allEach("1000"))));

        // Two members, not three investments
        assertThat(batch.getControlTotal()).isEqualByComparingTo("2000.00");
        assertThat(lines.getFirst().getAmountPerMember()).isEqualByComparingTo("1000.00");

        var preview = pnlService.preview(batch.getId());
        var ananyaRow = rowFor(preview, ananya);
        assertThat(ananyaRow.amount()).isEqualByComparingTo("1000.00");
        assertThat(ananyaRow.tranches()).extracting(PnlService.TranchePreview::investmentId)
                .containsExactly(first.getId(), topUp.getId());
        assertThat(ananyaRow.tranches()).extracting(PnlService.TranchePreview::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("714.29"), new BigDecimal("285.71"));
        assertThat(rowFor(preview, ravi).amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void fixedBatchWithMatchingControlTotalIsAccepted() {
        activeInvestments(activeInvestment(UUID.randomUUID(), "10000"), activeInvestment(UUID.randomUUID(), "10000"));
        storeBatchesAndLines();

        PnlBatch batch = pnlService.createBatch(maker, fixed(new BigDecimal("400.00"), checker, List.of(allEach("200"))));

        assertThat(batch.getControlTotal()).isEqualByComparingTo("400.00");
    }

    @Test
    void approvePostsEachInvestmentShareAndNotifiesTheMemberOnce() {
        UUID ananya = UUID.randomUUID();
        Investment first = activeInvestment(ananya, "50000");
        Investment topUp = activeInvestment(ananya, "20000");
        activeInvestments(first, topUp);
        storeBatchesAndLines();
        PnlBatch batch = pnlService.createBatch(maker, fixed(null, checker, List.of(memberEach(ananya, "1000"))));

        pnlService.approve(batch.getId(), checker);

        ArgumentCaptor<PnlAllocation> allocations = ArgumentCaptor.forClass(PnlAllocation.class);
        Mockito.verify(allocationRepository, Mockito.times(2)).saveAndFlush(allocations.capture());
        assertThat(allocations.getAllValues()).extracting(PnlAllocation::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("714.29"), new BigDecimal("285.71"));

        ArgumentCaptor<LedgerPostingService.Journal> journals = ArgumentCaptor.forClass(LedgerPostingService.Journal.class);
        Mockito.verify(ledgerPostingService, Mockito.times(2)).post(journals.capture());
        assertThat(journals.getAllValues()).extracting(LedgerPostingService.Journal::investmentId)
                .containsExactly(first.getId(), topUp.getId());

        Mockito.verify(outboxService, Mockito.times(1)).notifyUser(eq("PNL_POSTED"), eq(ananya),
                Mockito.contains("\"amount\":1000.00"));
        assertThat(batch.getStatus()).isEqualTo(PnlBatchStatus.APPROVED);
    }

    @Test
    void fixedApproveRefusesWhenMembersChanged() {
        Investment first = activeInvestment(UUID.randomUUID(), "10000");
        Investment joinedLater = activeInvestment(UUID.randomUUID(), "10000");
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE))
                .thenReturn(List.of(first))
                .thenReturn(List.of(first)) // create-time validation
                .thenReturn(List.of(first, joinedLater));
        storeBatchesAndLines();
        balance(first.getUserId(), "10000", "0");
        balance(joinedLater.getUserId(), "10000", "0");

        PnlBatch batch = pnlService.createBatch(maker, fixed(null, checker, List.of(allEach("200"))));

        assertThatThrownBy(() -> pnlService.approve(batch.getId(), checker))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION))
                .hasMessageContaining("200.00 per member");
        Mockito.verify(ledgerPostingService, Mockito.never()).post(any());
    }

    @Test
    void fixedTopUpAfterCreationDoesNotChangeTheMemberAmount() {
        UUID ananya = UUID.randomUUID();
        Investment first = activeInvestment(ananya, "50000");
        Investment topUp = activeInvestment(ananya, "20000");
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE))
                .thenReturn(List.of(first))
                .thenReturn(List.of(first))
                .thenReturn(List.of(first, topUp));
        storeBatchesAndLines();
        balance(ananya, "70000", "0");
        PnlBatch batch = pnlService.createBatch(maker, fixed(null, checker, List.of(allEach("1000"))));

        var preview = pnlService.preview(batch.getId());

        // Still ₹1,000 for Ananya, now split over both investments
        assertThat(preview.warnings()).isEmpty();
        assertThat(rowFor(preview, ananya).tranches()).hasSize(2);
    }

    // ---- PERCENTAGE: of the member's total principal ----

    @Test
    void percentageAppliesToEachMembersTotalPrincipal() {
        UUID ananya = UUID.randomUUID();
        UUID ravi = UUID.randomUUID();
        activeInvestments(activeInvestment(ananya, "50000"), activeInvestment(ravi, "10000"),
                activeInvestment(ananya, "20000"));
        List<PnlLine> lines = storeBatchesAndLines();

        PnlBatch batch = pnlService.createBatch(maker, percentage(null, List.of(allAt("2"))));

        assertThat(batch.getControlTotal()).isEqualByComparingTo("1600.00");
        assertThat(lines.getFirst().getPercentage()).isEqualByComparingTo("2");
        var preview = pnlService.preview(batch.getId());
        assertThat(rowFor(preview, ananya).amount()).isEqualByComparingTo("1400.00");
        assertThat(rowFor(preview, ravi).amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void percentageRoundsOnceOnTheMemberTotal() {
        UUID userId = UUID.randomUUID();
        // 3 x 3333.33 = 9999.99; 1% each would round to 33.33 x 3 = 99.99, member-level gives 100.00
        activeInvestments(activeInvestment(userId, "3333.33"), activeInvestment(userId, "3333.33"),
                activeInvestment(userId, "3333.33"));
        storeBatchesAndLines();

        PnlBatch batch = pnlService.createBatch(maker, percentage(null, List.of(allAt("1"))));

        assertThat(batch.getControlTotal()).isEqualByComparingTo("100.00");
        var row = pnlService.preview(batch.getId()).allocations().getFirst();
        assertThat(row.tranches().stream().map(PnlService.TranchePreview::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
    }

    @Test
    void negativePercentageRoundsToThePaisa() {
        UUID userId = UUID.randomUUID();
        activeInvestments(activeInvestment(userId, "10001"));
        storeBatchesAndLines();
        balance(userId, "10001", "0");

        PnlBatch batch = pnlService.createBatch(maker, percentage(null, List.of(allAt("-0.333"))));

        // 10001 x -0.333% = -33.30333 -> -33.30
        assertThat(batch.getControlTotal()).isEqualByComparingTo("-33.30");
    }

    @Test
    void suppliedControlTotalMustMatchComputedTotal() {
        activeInvestments(activeInvestment(UUID.randomUUID(), "10000"));
        storeBatchesAndLines();

        assertThatThrownBy(() -> pnlService.createBatch(maker, percentage(new BigDecimal("999.00"), List.of(allAt("1")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("control total");
    }

    @Test
    void percentageLineRequiresAPercentage() {
        var line = new PnlService.LineInput(PnlTargetType.ALL, null, null, null);

        assertThatThrownBy(() -> pnlService.createBatch(maker, percentage(null, List.of(line))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("percentage is required");
    }

    @Test
    void percentageOutOfRangeRejected() {
        assertThatThrownBy(() -> pnlService.createBatch(maker, percentage(null, List.of(allAt("-100")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("greater than -100");
    }

    @Test
    void percentageApproveRefusesWhenAMemberToppedUp() {
        UUID userId = UUID.randomUUID();
        Investment first = activeInvestment(userId, "10000");
        Investment topUp = activeInvestment(userId, "10000");
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE))
                .thenReturn(List.of(first))
                .thenReturn(List.of(first))
                .thenReturn(List.of(first, topUp));
        storeBatchesAndLines();
        balance(userId, "20000", "0");
        PnlBatch batch = pnlService.createBatch(maker, percentage(null, List.of(allAt("1"))));

        assertThatThrownBy(() -> pnlService.approve(batch.getId(), checker))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("active investments changed");
    }

    // ---- Principal on hold for a withdrawal ----

    @Test
    void principalOnHoldDoesNotEarnAndIsTakenFromTheOldestInvestment() {
        UUID ananya = UUID.randomUUID();
        Investment first = activeInvestment(ananya, "50000");
        Investment topUp = activeInvestment(ananya, "20000");
        activeInvestments(first, topUp);
        // ₹30,000 principal withdrawal requested, not yet paid: ledger principal is 40,000
        balance(ananya, "40000", "0");
        storeBatchesAndLines();

        PnlBatch batch = pnlService.createBatch(maker, percentage(null, List.of(allAt("1"))));

        assertThat(batch.getControlTotal()).isEqualByComparingTo("400.00");
        var row = pnlService.preview(batch.getId()).allocations().getFirst();
        assertThat(row.tranches()).extracting(PnlService.TranchePreview::earningPrincipal)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("20000"), new BigDecimal("20000"));
        assertThat(row.tranches()).extracting(PnlService.TranchePreview::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("200.00"), new BigDecimal("200.00"));
    }

    @Test
    void holdCoveringAWholeInvestmentLeavesItOutOfTheSplit() {
        UUID ananya = UUID.randomUUID();
        Investment first = activeInvestment(ananya, "50000");
        Investment topUp = activeInvestment(ananya, "20000");
        activeInvestments(first, topUp);
        balance(ananya, "20000", "0");

        var preview = pnlService.previewDraft(maker, fixed(null, null, List.of(memberEach(ananya, "1000"))));

        assertThat(preview.allocations().getFirst().tranches()).singleElement()
                .satisfies(t -> {
                    assertThat(t.investmentId()).isEqualTo(topUp.getId());
                    assertThat(t.amount()).isEqualByComparingTo("1000.00");
                });
    }

    @Test
    void memberExitingFullyIsSkippedByAllAndRefusedByMember() {
        UUID leaving = UUID.randomUUID();
        UUID staying = UUID.randomUUID();
        activeInvestments(activeInvestment(leaving, "10000"), activeInvestment(staying, "10000"));
        balance(leaving, "0", "0");

        var preview = pnlService.previewDraft(maker, fixed(null, null, List.of(allEach("100"))));
        assertThat(preview.allocations()).extracting(PnlService.AllocationPreview::userId).containsExactly(staying);
        assertThat(preview.controlTotal()).isEqualByComparingTo("100.00");

        assertThatThrownBy(() -> pnlService.previewDraft(maker, fixed(null, null, List.of(memberEach(leaving, "100")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("on hold for a withdrawal");
    }

    // ---- Loss floor ----

    @Test
    void lossFloorIsCheckedOnTheMemberTotal() {
        UUID userId = UUID.randomUUID();
        // Each investment alone could absorb -40000, together -80000 breaches 70000
        activeInvestments(activeInvestment(userId, "50000"), activeInvestment(userId, "20000"));
        storeBatchesAndLines();
        balance(userId, "70000", "0");

        assertThatThrownBy(() -> pnlService.createBatch(maker,
                fixed(null, checker, List.of(memberEach(userId, "-80000")))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Loss floor");
    }

    @Test
    void dryRunPreviewReportsTheLossFloor() {
        UUID userId = UUID.randomUUID();
        activeInvestments(activeInvestment(userId, "10000"));

        var draft = new PnlService.CreateBatchCommand(PnlBatchType.REGULAR, PnlValueType.FIXED, LocalDate.now(),
                null, null, null, null, null, List.of(memberEach(userId, "-20000")));

        assertThatThrownBy(() -> pnlService.previewDraft(maker, draft))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Loss floor");
    }

    // ---- Dry-run preview ----

    @Test
    void dryRunPreviewComputesAmountsWithoutSavingOrAnApprover() {
        activeInvestments(activeInvestment(UUID.randomUUID(), "10000"), activeInvestment(UUID.randomUUID(), "50000"));

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

    // ---- largeChange flag ----

    /** Saved-batch and dry-run previews for the same percentage, one member holding two investments. */
    private List<PnlService.PreviewResult> bothPreviews(String pct) {
        UUID userId = UUID.randomUUID();
        activeInvestments(activeInvestment(userId, "10000"), activeInvestment(userId, "50000"),
                activeInvestment(UUID.randomUUID(), "20000"));
        storeBatchesAndLines();
        PnlBatch batch = pnlService.createBatch(maker, percentage(null, List.of(allAt(pct))));
        return List.of(pnlService.preview(batch.getId()),
                pnlService.previewDraft(maker, percentage(null, List.of(allAt(pct)))));
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
    void largeChangeIsFlaggedPerMember() {
        for (var preview : bothPreviews("12")) {
            assertThat(preview.allocations()).hasSize(2);
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
    void fixedLargeChangeIsMeasuredAgainstTheMembersTotalPrincipal() {
        UUID userId = UUID.randomUUID();
        // ₹1,500 is over 10% of the ₹10,000 investment but under 10% of the member's ₹60,000
        activeInvestments(activeInvestment(userId, "10000"), activeInvestment(userId, "50000"));

        var preview = pnlService.previewDraft(maker, fixed(null, null, List.of(memberEach(userId, "1500"))));

        assertThat(preview.allocations().getFirst().largeChange()).isFalse();
    }

    @Test
    void savedPreviewReportsDriftAsABatchWarning() {
        Investment first = activeInvestment(UUID.randomUUID(), "10000");
        Investment joinedLater = activeInvestment(UUID.randomUUID(), "10000");
        when(investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE))
                .thenReturn(List.of(first))
                .thenReturn(List.of(first)) // create-time validation
                .thenReturn(List.of(first, joinedLater));
        storeBatchesAndLines();
        balance(first.getUserId(), "10000", "0");
        balance(joinedLater.getUserId(), "10000", "0");
        PnlBatch batch = pnlService.createBatch(maker, percentage(null, List.of(allAt("1"))));

        assertThat(pnlService.preview(batch.getId()).warnings())
                .singleElement().asString().contains("active investments changed");
    }
}
