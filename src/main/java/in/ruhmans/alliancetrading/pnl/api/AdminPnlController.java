package in.ruhmans.alliancetrading.pnl.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import in.ruhmans.alliancetrading.admin.domain.AdminRole;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.common.pagination.PageResponse;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.pnl.api.dto.PnlBatchRequest;
import in.ruhmans.alliancetrading.pnl.api.dto.PnlLineRequest;
import in.ruhmans.alliancetrading.pnl.domain.PnlAllocation;
import in.ruhmans.alliancetrading.pnl.domain.PnlBatch;
import in.ruhmans.alliancetrading.pnl.domain.PnlBatchStatus;
import in.ruhmans.alliancetrading.pnl.domain.PnlBatchType;
import in.ruhmans.alliancetrading.pnl.domain.PnlTargetType;
import in.ruhmans.alliancetrading.pnl.domain.PnlValueType;
import in.ruhmans.alliancetrading.pnl.service.PnlService;
import in.ruhmans.alliancetrading.user.service.UserDirectory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/pnl-batches")
public class AdminPnlController {

    private final PnlService pnlService;
    private final UserDirectory userDirectory;

    public AdminPnlController(PnlService pnlService, UserDirectory userDirectory) {
        this.pnlService = pnlService;
        this.userDirectory = userDirectory;
    }

    public record BatchResponse(UUID id, PnlBatchStatus status, PnlBatchType batchType, PnlValueType valueType,
                                LocalDate sessionDate, BigDecimal controlTotal, UUID makerId,
                                UUID assignedCheckerId, UUID checkerId, String notes, Instant createdAt,
                                Instant decidedAt) {}

    public record ApproverOption(UUID id, String email, String displayName, AdminRole role) {}

    public record TrancheAllocationResponse(UUID investmentId, BigDecimal amount) {}

    /** One member's posted amount; {@code tranches} is its split across their investments and sums to it. */
    public record AllocationResponse(UUID userId, String fullName, String mobile, BigDecimal amount,
                                     List<TrancheAllocationResponse> tranches) {}

    public record RejectBatchRequest(@Size(max = 500) String reason) {}

    /**
     * Newest first. {@code assignedToMe=true} lists drafts waiting for the caller's approval;
     * otherwise filters by {@code status} and {@code valueType} (both optional).
     */
    @GetMapping
    public ResponseEntity<PageResponse<BatchResponse>> list(
            @RequestParam(required = false) PnlBatchStatus status,
            @RequestParam(required = false) PnlValueType valueType,
            @RequestParam(defaultValue = "false") boolean assignedToMe,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageResponse.newestFirst(page, size);
        var batches = assignedToMe
                ? pnlService.awaitingApprovalBy(SecurityUtils.currentAdminId(), pageable)
                : pnlService.list(status, valueType, pageable);
        return ResponseEntity.ok(PageResponse.of(batches, AdminPnlController::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatchResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(pnlService.getBatch(id)));
    }

    /** Admins the caller can choose as approver for a new batch. */
    @GetMapping("/approvers")
    public ResponseEntity<List<ApproverOption>> approvers() {
        return ResponseEntity.ok(pnlService.eligibleApprovers(SecurityUtils.currentAdminId()).stream()
                .map(a -> new ApproverOption(a.getId(), a.getEmail(), a.getDisplayName(), a.getRole()))
                .toList());
    }

    @PostMapping
    public ResponseEntity<BatchResponse> createManual(@Valid @RequestBody PnlBatchRequest request) {
        PnlBatch batch = pnlService.createBatch(SecurityUtils.currentAdminId(), toCommand(request));
        return ResponseEntity.ok(toResponse(batch));
    }

    /**
     * Dry run of create: same body (approverAdminId optional), same validation and per-member
     * amounts, nothing saved. Returns the preview with {@code batchId: null}.
     */
    @PostMapping("/preview")
    public ResponseEntity<PnlService.PreviewResult> previewDraft(@Valid @RequestBody PnlBatchRequest request) {
        return ResponseEntity.ok(pnlService.previewDraft(SecurityUtils.currentAdminId(), toCommand(request)));
    }

    private PnlService.CreateBatchCommand toCommand(PnlBatchRequest request) {
        return new PnlService.CreateBatchCommand(
                request.originalBatchId() != null ? PnlBatchType.ADJUSTMENT : PnlBatchType.REGULAR,
                request.valueType(), request.sessionDate(), request.originalBatchId(),
                request.controlTotal(), request.evidenceDocumentId(), request.notes(),
                request.approverAdminId(), resolveLines(request.lines()));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<PnlService.PreviewResult> preview(@PathVariable UUID id) {
        return ResponseEntity.ok(pnlService.preview(id));
    }

    /** Member-level split of an approved batch, with each member's per-investment shares. */
    @GetMapping("/{id}/allocations")
    public ResponseEntity<List<AllocationResponse>> allocations(@PathVariable UUID id) {
        Map<UUID, List<PnlAllocation>> byMember = new LinkedHashMap<>();
        for (PnlAllocation a : pnlService.allocations(id)) {
            byMember.computeIfAbsent(a.getUserId(), k -> new ArrayList<>()).add(a);
        }
        var labels = userDirectory.labels(byMember.keySet());
        return ResponseEntity.ok(byMember.entrySet().stream().map(e -> {
            var label = labels.get(e.getKey());
            BigDecimal total = e.getValue().stream().map(PnlAllocation::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new AllocationResponse(e.getKey(),
                    label != null ? label.fullName() : null, label != null ? label.mobile() : null, total,
                    e.getValue().stream()
                            .map(a -> new TrancheAllocationResponse(a.getInvestmentId(), a.getAmount()))
                            .toList());
        }).toList());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id) {
        pnlService.approve(id, SecurityUtils.currentAdminId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id,
                                       @Valid @RequestBody(required = false) RejectBatchRequest request) {
        pnlService.reject(id, SecurityUtils.currentAdminId(), request != null ? request.reason() : null);
        return ResponseEntity.ok().build();
    }

    // ---- Helpers ----

    private List<PnlService.LineInput> resolveLines(List<PnlLineRequest> requests) {
        List<PnlService.LineInput> lines = new ArrayList<>();
        for (PnlLineRequest r : requests) {
            lines.add(new PnlService.LineInput(parseTargetType(r.targetType()), r.userId(),
                    r.amount(), r.percentage()));
        }
        return lines;
    }

    private static PnlTargetType parseTargetType(String raw) {
        try {
            return PnlTargetType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "targetType must be ALL or MEMBER");
        }
    }

    private static BatchResponse toResponse(PnlBatch b) {
        return new BatchResponse(b.getId(), b.getStatus(), b.getBatchType(), b.getValueType(), b.getSessionDate(),
                b.getControlTotal(), b.getMakerId(), b.getAssignedCheckerId(), b.getCheckerId(),
                b.getNotes(), b.getCreatedAt(), b.getApprovedAt());
    }
}
