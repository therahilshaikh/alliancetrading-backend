package in.ruhmans.alliancetrading.pnl.service;

import in.ruhmans.alliancetrading.user.service.UserDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import in.ruhmans.alliancetrading.admin.domain.AdminUser;
import in.ruhmans.alliancetrading.admin.repository.AdminUserRepository;
import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.common.money.MoneyUtils;
import in.ruhmans.alliancetrading.files.repository.DocumentRepository;
import in.ruhmans.alliancetrading.investment.domain.Investment;
import in.ruhmans.alliancetrading.investment.domain.InvestmentStatus;
import in.ruhmans.alliancetrading.investment.repository.InvestmentRepository;
import in.ruhmans.alliancetrading.ledger.domain.Bucket;
import in.ruhmans.alliancetrading.ledger.domain.EntryType;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PnlService {

    private static final BigDecimal WARNING_THRESHOLD_PCT = new BigDecimal("0.10");

    private final PnlBatchRepository batchRepository;
    private final PnlLineRepository lineRepository;
    private final PnlAllocationRepository allocationRepository;
    private final InvestmentRepository investmentRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final DocumentRepository documentRepository;
    private final AdminUserRepository adminUserRepository;
    private final LedgerPostingService ledgerPostingService;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final UserDirectory userDirectory;
    private final Clock clock;

    public PnlService(PnlBatchRepository batchRepository, PnlLineRepository lineRepository,
                      PnlAllocationRepository allocationRepository,
                      InvestmentRepository investmentRepository,
                      UserBalanceRepository userBalanceRepository, DocumentRepository documentRepository,
                      AdminUserRepository adminUserRepository, LedgerPostingService ledgerPostingService,
                      OutboxService outboxService, AuditService auditService,
                      UserDirectory userDirectory, Clock clock) {
        this.batchRepository = batchRepository;
        this.lineRepository = lineRepository;
        this.allocationRepository = allocationRepository;
        this.investmentRepository = investmentRepository;
        this.userBalanceRepository = userBalanceRepository;
        this.documentRepository = documentRepository;
        this.adminUserRepository = adminUserRepository;
        this.ledgerPostingService = ledgerPostingService;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.userDirectory = userDirectory;
        this.clock = clock;
    }

    /**
     * One batch line, applied to every target investment. FIXED batches use {@code amount} (rupees each
     * target receives); PERCENTAGE batches use {@code percentage} (percent of each target's principal,
     * e.g. 1.25 = 1.25%). The other field must be null.
     */
    public record LineInput(PnlTargetType targetType, UUID investmentId,
                            BigDecimal amount, BigDecimal percentage) {}

    /**
     * A new P&L batch. {@code approverAdminId} is the admin the maker picks to approve it (optional
     * for a dry-run preview); {@code evidenceDocumentId} is optional. {@code controlTotal} is optional:
     * the server computes the total, and if one is supplied it must match.
     */
    public record CreateBatchCommand(PnlBatchType batchType, PnlValueType valueType, LocalDate sessionDate,
                                     UUID originalBatchId, BigDecimal controlTotal, UUID evidenceDocumentId,
                                     String notes, UUID approverAdminId, List<LineInput> lines) {}

    @Transactional
    public PnlBatch createBatch(UUID makerId, CreateBatchCommand command) {
        Draft draft = buildDraft(makerId, command, true);
        PnlBatch batch = batchRepository.save(draft.batch());
        draft.lines().forEach(lineRepository::save);

        // Loss floor and target validation against the saved lines
        computeAllocations(batch, lineRepository.findByBatchId(batch.getId()));

        auditService.recordAdmin(makerId, "PNL_BATCH_CREATED", "pnl_batch",
                batch.getId().toString(), null,
                AuditService.json("valueType", batch.getValueType(), "controlTotal", batch.getControlTotal(),
                        "approverAdminId", command.approverAdminId()));
        return batch;
    }

    /**
     * Dry run of {@link #createBatch}: the same validation and per-investment amounts, nothing saved.
     * Lets the console show real totals (percentages depend on each member's principal) before posting.
     */
    @Transactional(readOnly = true)
    public PreviewResult previewDraft(UUID makerId, CreateBatchCommand command) {
        Draft draft = buildDraft(makerId, command, false);
        return toPreview(null, draft.batch(), computeAllocations(draft.batch(), draft.lines()));
    }

    public record AllocationPreview(UUID investmentId, UUID userId, String fullName, String mobile,
                                    BigDecimal amount,
                                    BigDecimal principal, BigDecimal profit,
                                    BigDecimal resultingPrincipal, BigDecimal resultingProfit,
                                    boolean largeChange) {}

    /**
     * {@code batchId} is null for a dry-run preview. {@code largeChangeThresholdPct} is the percent of principal
     * above which a row is flagged {@code largeChange}; {@code warnings} holds batch-level messages only.
     */
    public record PreviewResult(UUID batchId, PnlValueType valueType, BigDecimal controlTotal, LocalDate sessionDate,
                                BigDecimal largeChangeThresholdPct,
                                List<AllocationPreview> allocations, List<String> warnings) {}

    @Transactional(readOnly = true)
    public PreviewResult preview(UUID batchId) {
        PnlBatch batch = getBatch(batchId);
        return toPreview(batchId, batch, computeAllocations(batch, lineRepository.findByBatchId(batchId)));
    }

    private static PreviewResult toPreview(UUID batchId, PnlBatch batch, Resolved resolved) {
        List<String> warnings = new ArrayList<>(resolved.drift);
        return new PreviewResult(batchId, batch.getValueType(), batch.getControlTotal(), batch.getSessionDate(),
                WARNING_THRESHOLD_PCT.movePointRight(2), new ArrayList<>(resolved.previews), warnings);
    }

    @Transactional
    public void approve(UUID batchId, UUID checkerId) {
        Instant now = clock.instant();
        PnlBatch batch = getBatch(batchId);
        if (batch.getStatus() != PnlBatchStatus.DRAFT) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION, "Batch is not in DRAFT state");
        }
        requireDecider(batch, checkerId);

        // Re-validate everything on approve
        Resolved resolved = computeAllocations(batch, lineRepository.findByBatchId(batchId));
        if (!resolved.drift.isEmpty()) {
            // Percentage batches: the approver must post exactly what was previewed
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    String.join("; ", resolved.drift) + ". Reject this batch and create a new one.");
        }

        for (var e : resolved.allocations.entrySet()) {
            Investment inv = resolved.investments.get(e.getKey());
            BigDecimal amount = e.getValue();

            // Write allocation row (REGULAR uniqueness enforced by DB unique index)
            PnlAllocation alloc = new PnlAllocation(batchId, batch.getBatchType(), inv.getId(),
                    inv.getUserId(), batch.getSessionDate(), amount, now);
            try {
                allocationRepository.saveAndFlush(alloc);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                        "Investment already has REGULAR P&L for session date " + batch.getSessionDate());
            }

            // Post to ledger
            ledgerPostingService.post(new LedgerPostingService.Journal(
                    inv.getUserId(), inv.getId(),
                    List.of(new LedgerPostingService.Posting(Bucket.PROFIT,
                            batch.getBatchType() == PnlBatchType.ADJUSTMENT
                                    ? EntryType.PNL_ADJUSTMENT : EntryType.PNL,
                            amount)),
                    "pnl_batch", batchId.toString(),
                    "pnl:" + batchId + ":" + inv.getId(),
                    "ADMIN", checkerId));

            outboxService.notifyUser("PNL_POSTED", inv.getUserId(),
                    "{\"batchId\":\"" + batchId + "\",\"amount\":" + amount + "}");
        }

        batch.approve(checkerId, now);
        batchRepository.save(batch);
        auditService.recordAdmin(checkerId, "PNL_BATCH_APPROVED", "pnl_batch", batchId.toString(),
                null, null);
    }

    @Transactional
    public void reject(UUID batchId, UUID checkerId, String reason) {
        PnlBatch batch = getBatch(batchId);
        if (batch.getStatus() != PnlBatchStatus.DRAFT) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION, "Batch is not in DRAFT state");
        }
        requireDecider(batch, checkerId);
        batch.reject(checkerId, clock.instant());
        batchRepository.save(batch);
        auditService.recordAdmin(checkerId, "PNL_BATCH_REJECTED", "pnl_batch", batchId.toString(),
                null, AuditService.json("reason", reason));
    }

    @Transactional(readOnly = true)
    public PnlBatch getBatch(UUID batchId) {
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "P&L batch not found"));
    }

    /** Filters are optional; {@code assignedToMe} drafts are handled by {@link #awaitingApprovalBy}. */
    @Transactional(readOnly = true)
    public Page<PnlBatch> list(PnlBatchStatus status, PnlValueType valueType, Pageable pageable) {
        if (status != null && valueType != null) {
            return batchRepository.findByStatusAndValueType(status, valueType, pageable);
        }
        if (status != null) {
            return batchRepository.findByStatus(status, pageable);
        }
        return valueType != null ? batchRepository.findByValueType(valueType, pageable)
                : batchRepository.findAll(pageable);
    }

    /** Draft batches waiting for this admin's decision. */
    @Transactional(readOnly = true)
    public Page<PnlBatch> awaitingApprovalBy(UUID adminId, Pageable pageable) {
        return batchRepository.findByAssignedCheckerIdAndStatus(adminId, PnlBatchStatus.DRAFT, pageable);
    }

    /** Per-user split written when the batch was approved (empty until then). */
    @Transactional(readOnly = true)
    public List<PnlAllocation> allocations(UUID batchId) {
        getBatch(batchId);
        return allocationRepository.findByBatchId(batchId);
    }

    /** Active admins, other than the maker, who can be picked as approver. */
    @Transactional(readOnly = true)
    public List<AdminUser> eligibleApprovers(UUID makerId) {
        return adminUserRepository.findByStatusOrderByEmailAsc("ACTIVE").stream()
                .filter(a -> !a.getId().equals(makerId))
                .toList();
    }

    // ---- Internal ----

    private void requireEligibleApprover(UUID approverAdminId, UUID makerId) {
        if (approverAdminId == null) {
            throw new ApiException(ErrorCode.INVALID_APPROVER, "Select an admin to approve this batch");
        }
        if (approverAdminId.equals(makerId)) {
            throw new ApiException(ErrorCode.INVALID_APPROVER, "You cannot approve your own batch");
        }
        AdminUser approver = adminUserRepository.findById(approverAdminId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_APPROVER, "Approver not found"));
        if (!approver.isActive()) {
            throw new ApiException(ErrorCode.INVALID_APPROVER, "Approver is not an active admin");
        }
    }

    /** Only the assigned approver may decide; legacy batches fall back to "anyone but the maker". */
    private void requireDecider(PnlBatch batch, UUID adminId) {
        if (batch.getMakerId().equals(adminId)) {
            throw new ApiException(ErrorCode.MAKER_CHECKER_VIOLATION,
                    "Approver must be different from the maker");
        }
        if (batch.getAssignedCheckerId() != null && !batch.getAssignedCheckerId().equals(adminId)) {
            throw new ApiException(ErrorCode.MAKER_CHECKER_VIOLATION,
                    "This batch is assigned to another admin for approval");
        }
    }

    /** {@code drift}: lines whose recomputed total no longer matches the total stored at creation. */
    private record Resolved(Map<UUID, BigDecimal> allocations,
                            Map<UUID, Investment> investments,
                            List<AllocationPreview> previews,
                            List<String> drift) {}

    /** A validated, unsaved batch and its lines (line totals computed). */
    private record Draft(PnlBatch batch, List<PnlLine> lines) {}

    /**
     * Validates a create/preview request and computes every line's total from its targets.
     * {@code requireApprover} is false for a dry-run preview.
     */
    private Draft buildDraft(UUID makerId, CreateBatchCommand command, boolean requireApprover) {
        PnlValueType valueType = command.valueType();
        List<LineInput> inputs = command.lines() == null ? List.of() : command.lines();
        if (valueType == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "valueType must be FIXED or PERCENTAGE");
        }
        if (command.sessionDate() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "sessionDate is required");
        }
        if (inputs.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Batch must contain at least one line");
        }
        if (command.batchType() == PnlBatchType.ADJUSTMENT && command.originalBatchId() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Adjustment batch must reference an original batch");
        }
        if (requireApprover || command.approverAdminId() != null) {
            requireEligibleApprover(command.approverAdminId(), makerId);
        }
        if (command.evidenceDocumentId() != null) {
            documentRepository.findById(command.evidenceDocumentId())
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "Evidence document not found"));
        }

        // Batch id is needed by the lines; the batch is only persisted by createBatch
        PnlBatch batch = new PnlBatch(command.batchType(), valueType, command.sessionDate(),
                command.originalBatchId(), MoneyUtils.ZERO, command.evidenceDocumentId(), makerId,
                command.approverAdminId(), command.notes(), clock.instant());

        List<PnlLine> lines = new ArrayList<>();
        BigDecimal sum = MoneyUtils.ZERO;
        for (LineInput li : inputs) {
            if (li.targetType() == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "targetType is required for every line");
            }
            BigDecimal perInvestment = valueType == PnlValueType.FIXED ? requireFixedAmount(li) : null;
            BigDecimal percentage = valueType == PnlValueType.PERCENTAGE ? requirePercentage(li) : null;
            List<Investment> targets = resolveTargets(li.targetType(), li.investmentId());
            BigDecimal lineTotal = total(lineSplit(targets, perInvestment, percentage));
            lines.add(new PnlLine(batch.getId(), li.targetType(), li.investmentId(),
                    lineTotal, percentage, perInvestment));
            sum = sum.add(lineTotal);
        }

        if (command.controlTotal() != null && MoneyUtils.money(command.controlTotal()).compareTo(sum) != 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Sum of lines (" + sum + ") does not equal control total ("
                    + MoneyUtils.money(command.controlTotal()) + ")");
        }
        batch.setControlTotal(sum);
        return new Draft(batch, lines);
    }

    /**
     * Resolves all lines to per-investment allocations. Validates:
     * targets exist, investments ACTIVE, loss floor respected.
     */
    private Resolved computeAllocations(PnlBatch batch, List<PnlLine> lines) {
        Map<UUID, BigDecimal> allocations = new LinkedHashMap<>();
        Map<UUID, Investment> investments = new HashMap<>();
        List<AllocationPreview> previews = new ArrayList<>();
        List<String> drift = new ArrayList<>();

        for (PnlLine line : lines) {
            List<Investment> targets = resolveTargets(line.getTargetType(), line.getInvestmentId());
            targets.forEach(inv -> investments.put(inv.getId(), inv));

            Map<UUID, BigDecimal> split = lineSplit(targets, line.getAmountPerInvestment(), line.getPercentage());
            BigDecimal recomputed = total(split);
            if (recomputed.compareTo(line.getAmount()) != 0) {
                String rate = line.getPercentage() != null ? line.getPercentage() + "%" : line.getAmountPerInvestment() + " each";
                drift.add(line.getTargetType() + " line at " + rate + " now totals " + recomputed
                        + " instead of " + line.getAmount()
                        + " because active investments changed since the batch was created");
            }
            split.forEach((invId, amt) -> allocations.merge(invId, amt, BigDecimal::add));
        }

        // Loss floor + previews; member names/mobiles in one batched lookup
        Map<UUID, UserDirectory.UserLabel> labels = userDirectory.labels(
                allocations.keySet().stream().map(id -> investments.get(id).getUserId()).collect(java.util.stream.Collectors.toSet()));
        for (var e : allocations.entrySet()) {
            Investment inv = investments.get(e.getKey());
            UserBalance balance = userBalanceRepository.findById(inv.getUserId())
                    .orElse(new UserBalance(inv.getUserId(), clock.instant()));
            BigDecimal currentValue = balance.getPrincipal().add(balance.getProfit());
            if (currentValue.add(e.getValue()).signum() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Loss floor breach for investment " + inv.getId()
                        + ": current value " + currentValue + " + delta " + e.getValue() + " < 0");
            }
            UserDirectory.UserLabel label = labels.get(inv.getUserId());
            boolean largeChange = e.getValue().abs()
                    .compareTo(inv.getAmount().multiply(WARNING_THRESHOLD_PCT)) > 0;
            previews.add(new AllocationPreview(inv.getId(), inv.getUserId(),
                    label != null ? label.fullName() : null, label != null ? label.mobile() : null,
                    e.getValue(),
                    balance.getPrincipal(), balance.getProfit(),
                    balance.getPrincipal(), balance.getProfit().add(e.getValue()),
                    largeChange));
        }

        return new Resolved(allocations, investments, previews, drift);
    }

    /** The ACTIVE investments a line applies to. */
    private List<Investment> resolveTargets(PnlTargetType targetType, UUID investmentId) {
        return switch (targetType) {
            case INVESTMENT -> {
                if (investmentId == null) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "investmentId is required for INVESTMENT lines");
                }
                yield List.of(resolveInvestment(investmentId));
            }
            case ALL -> {
                List<Investment> active = investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE);
                if (active.isEmpty()) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "There are no ACTIVE investments");
                }
                yield active;
            }
        };
    }

    /**
     * What each target receives: the fixed amount (FIXED) or remaining principal x percentage / 100
     * rounded to the paisa (PERCENTAGE). Exactly one of {@code perInvestment} / {@code percentage} is set.
     */
    private static Map<UUID, BigDecimal> lineSplit(List<Investment> targets, BigDecimal perInvestment,
                                                   BigDecimal percentage) {
        Map<UUID, BigDecimal> split = new LinkedHashMap<>();
        for (Investment inv : targets) {
            split.put(inv.getId(), perInvestment != null
                    ? perInvestment
                    : MoneyUtils.money(inv.getRemainingPrincipal().multiply(percentage).movePointLeft(2)));
        }
        return split;
    }

    private static BigDecimal total(Map<UUID, BigDecimal> split) {
        return split.values().stream().reduce(MoneyUtils.ZERO, BigDecimal::add);
    }

    private static BigDecimal requireFixedAmount(LineInput li) {
        if (li.percentage() != null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "percentage is only allowed when valueType is PERCENTAGE");
        }
        if (li.amount() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "amount is required for every FIXED line");
        }
        BigDecimal amount = MoneyUtils.money(li.amount());
        if (amount.signum() == 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "amount must be non-zero");
        }
        return amount;
    }

    private static BigDecimal requirePercentage(LineInput li) {
        if (li.amount() != null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "amount is only allowed when valueType is FIXED");
        }
        return normalizePercentage(li.percentage());
    }

    private static BigDecimal normalizePercentage(BigDecimal percentage) {
        if (percentage == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "percentage is required for every line");
        }
        BigDecimal p = percentage.setScale(MoneyUtils.PERCENT_SCALE, java.math.RoundingMode.HALF_UP);
        if (p.signum() == 0 || p.compareTo(new BigDecimal("-100")) <= 0 || p.compareTo(new BigDecimal("100")) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "percentage must be non-zero, greater than -100 and at most 100");
        }
        return p;
    }

    private Investment resolveInvestment(UUID investmentId) {
        Investment inv = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Investment not found: " + investmentId));
        if (inv.getStatus() != InvestmentStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Investment " + investmentId + " is not ACTIVE");
        }
        return inv;
    }
}
