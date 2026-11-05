package in.ruhmans.alliancetrading.investment.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import in.ruhmans.alliancetrading.common.pagination.PageResponse;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.investment.api.dto.AdminInvestmentResponse;
import in.ruhmans.alliancetrading.investment.api.dto.RejectInvestmentRequest;
import in.ruhmans.alliancetrading.investment.domain.Investment;
import in.ruhmans.alliancetrading.investment.domain.InvestmentStatus;
import in.ruhmans.alliancetrading.investment.service.InvestmentService;
import in.ruhmans.alliancetrading.payment.service.PaymentService;
import in.ruhmans.alliancetrading.user.service.UserDirectory;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/investments")
public class AdminInvestmentController {

    private final InvestmentService investmentService;
    private final UserDirectory userDirectory;
    private final PaymentService paymentService;

    public AdminInvestmentController(InvestmentService investmentService, UserDirectory userDirectory,
                                     PaymentService paymentService) {
        this.investmentService = investmentService;
        this.userDirectory = userDirectory;
        this.paymentService = paymentService;
    }

    /** Newest first. */
    @GetMapping
    public ResponseEntity<PageResponse<AdminInvestmentResponse>> list(
            @RequestParam(defaultValue = "PENDING_VERIFICATION") InvestmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var investments = investmentService.listByStatus(status, PageResponse.newestFirst(page, size));
        return ResponseEntity.ok(PageResponse.of(investments, toResponses(investments.getContent())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminInvestmentResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponses(List.of(investmentService.getById(id))).getFirst());
    }

    /** Member labels and expected payments for the whole page in batched lookups. */
    private List<AdminInvestmentResponse> toResponses(List<Investment> investments) {
        var labels = userDirectory.labels(investments.stream().map(Investment::getUserId)
                .collect(Collectors.toSet()));
        var expected = paymentService.expectedByAssignmentId(investments.stream()
                .map(Investment::getPaymentAssignmentId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return investments.stream()
                .map(i -> AdminInvestmentResponse.from(i, labels.get(i.getUserId()),
                        i.getPaymentAssignmentId() != null ? expected.get(i.getPaymentAssignmentId()) : null))
                .toList();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id) {
        investmentService.approve(id, SecurityUtils.currentAdminId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id,
                                       @Valid @RequestBody RejectInvestmentRequest request) {
        investmentService.reject(id, SecurityUtils.currentAdminId(), request.reason());
        return ResponseEntity.ok().build();
    }
}
