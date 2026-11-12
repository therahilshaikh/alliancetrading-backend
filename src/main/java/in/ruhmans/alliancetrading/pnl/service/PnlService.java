package in.ruhmans.alliancetrading.pnl.service;

import in.ruhmans.alliancetrading.user.service.UserDirectory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/**
 * P&L batches with maker-checker approval. P&L is decided per <b>member</b>: FIXED gives each target member
 * the same rupee amount, PERCENTAGE gives each member a percent of their total remaining principal. Either
 * way the member amount is then split across the member's ACTIVE investments by {@link PnlAllocator}, so a
 * member with several investments is paid once, not once per investment.
 */
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
     * One batch line. {@code targetType} ALL = every member with an ACTIVE investment, MEMBER = one member
     * ({@code userId}). FIXED batches use {@code amount} (rupees each target member receives); PERCENTAGE
     * batches use {@code percentage} (percent of each member's total remaining principal, e.g. 1.25 = 1.25%).
     * The other field must be null.
     */
    public record LineInput(PnlTargetType targetType, UUID userId,
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
        computeAllocations(lineRepository.findByBatchId(batch.getId()));

        auditService.recordAdmin(makerId, "PNL_BATCH_CREATED", "pnl_batch",
                batch.getId().toString(), null,
                AuditService.json("valueType", batch.getValueType(), "controlTotal", batch.getControlTotal(),
                        "approverAdminId", command.approverAdminId()));
        return batch;
    }

    /**
     * Dry run of {@link #createBatch}: the same validation and per-member amounts, nothing saved.
     * Lets the console show real totals (percentages depend on each member's principal) before posting.
     */
    @Transactional(readOnly = true)
    public PreviewResult previewDraft(UUID makerId, CreateBatchCommand command) {
        Draft draft = buildDraft(makerId, command, false);
        return toPreview(null, draft.batch(), computeAllocations(draft.lines()));
    }

    /**
     * One investment's share of a member's amount. {@code earningPrincipal} is its remaining principal less any
     * part on hold for an open principal withdrawal.
     */
    public record TranchePreview(UUID investmentId, BigDecimal earningPrincipal, BigDecimal amount) {}

    /**
     * One member's line in a preview. {@code principal} is the member's ledger principal (excluding any on hold);
     * {@code largeChange} is true when {@code amount} exceeds the threshold percent of the member's earning
     * principal. {@code tranches} always sums to {@code amount}.
     */
    public record AllocationPreview(UUID userId, String fullName, String mobile,
                                    BigDecimal amount,
                                    BigDecimal principal, BigDecimal profit,
                                    BigDecimal resultingPrincipal, BigDecimal resultingProfit,
                                    boolean largeChange, List<TranchePreview> tranches) {}

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
        return toPreview(batchId, batch, computeAllocations(lineRepository.findByBatchId(batchId)));
    }

    private static PreviewResult toPreview(UUID batchId, PnlBatch batch, Resolved resolved) {
        return new PreviewResult(batchId, batch.getValueType(), batch.getControlTotal(), batch.getSessionDate(),
                WARNING_THRESHOLD_PCT.movePointRight(2), List.copyOf(resolved.previews()),
                List.copyOf(resolved.drift()));
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
        Resolved resolved = computeAllocations(lineRepository.findByBatchId(batchId));
        if (!resolved.drift().isEmpty()) {
            // The approver must post exactly what was previewed
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    String.join("; ", resolved.drift()) + ". Reject this batch and create a new one.");
        }

        EntryType entryType = batch.getBatchType() == PnlBatchType.ADJUSTMENT
                ? EntryType.PNL_ADJUSTMENT : EntryType.PNL;
        for (MemberAllocation member : resolved.members()) {
            for (var share : member.tranches().entrySet()) {
                UUID investmentId = share.getKey();
                BigDecimal amount = share.getValue();

                // Allocation row (REGULAR once per investment per day, enforced by DB unique index)
                try {
                    allocationRepository.saveAndFlush(new PnlAllocation(batchId, batch.getBatchType(),
                            investmentId, member.userId(), batch.getSessionDate(), amount, now));
                } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                    throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                            "Investment already has REGULAR P&L for session date " + batch.getSessionDate());
                }

                ledgerPostingService.post(new LedgerPostingService.Journal(
                        member.userId(), investmentId,
                        List.of(new LedgerPostingService.Posting(Bucket.PROFIT, entryType, amount)),
                        "pnl_batch", batchId.toString(),
                        "pnl:" + batchId + ":" + investmentId,
                        "ADMIN", checkerId));
            }

            // One message per member with their total, however many investments it was split across
            outboxService.notifyUser("PNL_POSTED", member.userId(),
                    "{\"batchId\":\"" + batchId + "\",\"amount\":" + member.amount() + "}");
        }

        batch.approve(checkerId, now);
        batchRepository.save(batch);
        auditService.recordAdmin(checkerId, "PNL_BATCH_APPROVED", "pnl_batch", batchId.toString(),
                null, AuditService.json("members", resolved.members().size(),
                        "controlTotal", batch.getControlTotal()));
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

    /** Per-investment rows written when the batch was approved (empty until then). */
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

    /**
     * A member's ACTIVE investments and the principal each still has earning. Principal on hold for an open
     * withdrawal has already left the ledger balance but not yet the investments (they are debited at payout),
     * so the difference is taken off the investments oldest first, the same order payout uses.
     * {@code earning} holds only investments with principal left, oldest first.
     */
    private record MemberPosition(UUID userId, List<Investment> investments, Map<UUID, BigDecimal> earning,
                                  BigDecimal earningPrincipal, UserBalance balance) {}

    /** A member's amount and its split across the investments still earning (oldest first). */
    private record MemberAllocation(MemberPosition position, BigDecimal amount, Map<UUID, BigDecimal> tranches) {
        UUID userId() { return position.userId(); }
    }

    /** {@code drift}: lines whose recomputed total no longer matches the total stored at creation. */
    private record Resolved(List<MemberAllocation> members, List<AllocationPreview> previews,
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
            BigDecimal perMember = valueType == PnlValueType.FIXED ? requireFixedAmount(li) : null;
            BigDecimal percentage = valueType == PnlValueType.PERCENTAGE ? requirePercentage(li) : null;
            BigDecimal lineTotal = resolveMembers(li.targetType(), li.userId()).values().stream()
                    .map(position -> memberAmount(position, perMember, percentage))
                    .reduce(MoneyUtils.ZERO, BigDecimal::add);
            lines.add(new PnlLine(batch.getId(), li.targetType(), li.userId(), lineTotal, percentage, perMember));
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
     * Resolves all lines to per-member amounts and their per-investment split. Validates: every target member
     * holds an ACTIVE investment, no member is targeted twice, and the loss floor holds for each member.
     */
    private Resolved computeAllocations(List<PnlLine> lines) {
        Map<UUID, MemberAllocation> members = new LinkedHashMap<>();
        List<String> drift = new ArrayList<>();

        for (PnlLine line : lines) {
            BigDecimal recomputed = MoneyUtils.ZERO;
            for (MemberPosition position : resolveMembers(line.getTargetType(), line.getUserId()).values()) {
                UUID userId = position.userId();
                if (members.containsKey(userId)) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED,
                            "Member " + userId + " is targeted by more than one line; use one line per member");
                }
                BigDecimal amount = memberAmount(position, line.getAmountPerMember(), line.getPercentage());
                members.put(userId, new MemberAllocation(position, amount,
                        PnlAllocator.split(amount, position.earning())));
                recomputed = recomputed.add(amount);
            }
            if (recomputed.compareTo(line.getAmount()) != 0) {
                String rate = line.getPercentage() != null
                        ? line.getPercentage().stripTrailingZeros().toPlainString() + "%"
                        : line.getAmountPerMember() + " per member";
                drift.add(line.getTargetType() + " line at " + rate + " now totals " + recomputed
                        + " instead of " + line.getAmount()
                        + " because members' active investments changed since the batch was created");
            }
        }

        // Loss floor + previews; member names/mobiles in one batched lookup
        Map<UUID, UserDirectory.UserLabel> labels = userDirectory.labels(Set.copyOf(members.keySet()));
        List<AllocationPreview> previews = new ArrayList<>();
        for (MemberAllocation m : members.values()) {
            UserBalance balance = m.position().balance();
            BigDecimal currentValue = balance.getPrincipal().add(balance.getProfit());
            if (currentValue.add(m.amount()).signum() < 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Loss floor breach for member " + m.userId()
                        + ": current value " + currentValue + " + delta " + m.amount() + " < 0");
            }
            UserDirectory.UserLabel label = labels.get(m.userId());
            boolean largeChange = m.amount().abs()
                    .compareTo(m.position().earningPrincipal().multiply(WARNING_THRESHOLD_PCT)) > 0;
            List<TranchePreview> tranches = m.position().earning().entrySet().stream()
                    .map(e -> new TranchePreview(e.getKey(), e.getValue(), m.tranches().get(e.getKey())))
                    .toList();
            previews.add(new AllocationPreview(m.userId(),
                    label != null ? label.fullName() : null, label != null ? label.mobile() : null,
                    m.amount(),
                    balance.getPrincipal(), balance.getProfit(),
                    balance.getPrincipal(), balance.getProfit().add(m.amount()),
                    largeChange, tranches));
        }

        return new Resolved(List.copyOf(members.values()), previews, drift);
    }

    /**
     * Target members and their earning positions, in a stable order. ALL skips members whose whole principal
     * is on hold for a withdrawal; a MEMBER line for such a member is refused.
     */
    private Map<UUID, MemberPosition> resolveMembers(PnlTargetType targetType, UUID userId) {
        return switch (targetType) {
            case MEMBER -> {
                if (userId == null) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "userId is required for MEMBER lines");
                }
                List<Investment> active = investmentRepository.findActiveByUserIdOldestFirst(userId);
                if (active.isEmpty()) {
                    throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                            "Member " + userId + " has no ACTIVE investment");
                }
                MemberPosition position = position(userId, active);
                if (position.earning().isEmpty()) {
                    throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                            "Member " + userId + " has all principal on hold for a withdrawal");
                }
                yield Map.of(userId, position);
            }
            case ALL -> {
                if (userId != null) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "userId is only allowed for MEMBER lines");
                }
                List<Investment> active = investmentRepository.findByStatusOrderByCreatedAt(InvestmentStatus.ACTIVE);
                Map<UUID, List<Investment>> byMember = new LinkedHashMap<>();
                for (Investment inv : active) {
                    byMember.computeIfAbsent(inv.getUserId(), k -> new ArrayList<>()).add(inv);
                }
                Map<UUID, MemberPosition> positions = new LinkedHashMap<>();
                byMember.forEach((id, investments) -> {
                    MemberPosition position = position(id, investments);
                    if (!position.earning().isEmpty()) {
                        positions.put(id, position);
                    }
                });
                if (positions.isEmpty()) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "There are no ACTIVE investments");
                }
                yield positions;
            }
        };
    }

    /** See {@link MemberPosition}. {@code investments} are the member's ACTIVE investments, oldest first. */
    private MemberPosition position(UUID userId, List<Investment> investments) {
        UserBalance balance = userBalanceRepository.findById(userId)
                .orElse(new UserBalance(userId, clock.instant()));
        BigDecimal invested = investments.stream().map(Investment::getRemainingPrincipal)
                .reduce(MoneyUtils.ZERO, BigDecimal::add);
        BigDecimal held = invested.subtract(balance.getPrincipal()).max(MoneyUtils.ZERO);

        Map<UUID, BigDecimal> earning = new LinkedHashMap<>();
        BigDecimal earningPrincipal = MoneyUtils.ZERO;
        for (Investment inv : investments) {
            BigDecimal taken = inv.getRemainingPrincipal().min(held);
            held = held.subtract(taken);
            BigDecimal left = inv.getRemainingPrincipal().subtract(taken);
            if (left.signum() > 0) {
                earning.put(inv.getId(), left);
                earningPrincipal = earningPrincipal.add(left);
            }
        }
        return new MemberPosition(userId, investments, earning, earningPrincipal, balance);
    }

    /**
     * What a member receives: the fixed amount (FIXED), or their earning principal x percentage / 100
     * rounded to the paisa (PERCENTAGE). Exactly one of {@code perMember} / {@code percentage} is set.
     */
    private static BigDecimal memberAmount(MemberPosition position, BigDecimal perMember, BigDecimal percentage) {
        if (perMember != null) {
            return perMember;
        }
        return MoneyUtils.money(position.earningPrincipal().multiply(percentage).movePointLeft(2));
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
        BigDecimal p = percentage.setScale(MoneyUtils.PERCENT_SCALE, RoundingMode.HALF_UP);
        if (p.signum() == 0 || p.compareTo(new BigDecimal("-100")) <= 0 || p.compareTo(new BigDecimal("100")) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "percentage must be non-zero, greater than -100 and at most 100");
        }
        return p;
    }
}
