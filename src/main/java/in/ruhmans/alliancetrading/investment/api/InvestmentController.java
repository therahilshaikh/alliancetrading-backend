package in.ruhmans.alliancetrading.investment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import in.ruhmans.alliancetrading.common.idempotency.IdempotencyService;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.investment.api.dto.InvestmentRequest;
import in.ruhmans.alliancetrading.investment.api.dto.InvestmentResponse;
import in.ruhmans.alliancetrading.investment.domain.Investment;
import in.ruhmans.alliancetrading.investment.service.InvestmentService;
import in.ruhmans.alliancetrading.pnl.repository.PnlAllocationRepository;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/investments")
public class InvestmentController {

    private final InvestmentService investmentService;
    private final PnlAllocationRepository pnlAllocationRepository;

    public InvestmentController(InvestmentService investmentService,
                                PnlAllocationRepository pnlAllocationRepository) {
        this.investmentService = investmentService;
        this.pnlAllocationRepository = pnlAllocationRepository;
    }

    @PostMapping
    public ResponseEntity<InvestmentResponse> submit(
            @Valid @RequestBody InvestmentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        String key = IdempotencyService.requireKey(idempotencyKey);
        var investment = investmentService.submit(SecurityUtils.currentUserId(), request, key);
        return ResponseEntity.ok(InvestmentResponse.from(investment, BigDecimal.ZERO));
    }

    @GetMapping
    public ResponseEntity<List<InvestmentResponse>> getOwn() {
        var investments = investmentService.getOwn(SecurityUtils.currentUserId());
        if (investments.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        Map<UUID, BigDecimal> earned = pnlAllocationRepository.sumByInvestmentIds(investments.stream()
                .map(Investment::getId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PnlAllocationRepository.InvestmentTotal::getInvestmentId,
                        PnlAllocationRepository.InvestmentTotal::getTotal));
        return ResponseEntity.ok(investments.stream()
                .map(i -> InvestmentResponse.from(i, earned.getOrDefault(i.getId(), BigDecimal.ZERO)))
                .toList());
    }
}
