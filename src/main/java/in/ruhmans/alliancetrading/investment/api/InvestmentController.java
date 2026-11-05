package in.ruhmans.alliancetrading.investment.api;

import java.util.List;

import in.ruhmans.alliancetrading.common.idempotency.IdempotencyService;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.investment.api.dto.InvestmentRequest;
import in.ruhmans.alliancetrading.investment.api.dto.InvestmentResponse;
import in.ruhmans.alliancetrading.investment.domain.Investment;
import in.ruhmans.alliancetrading.investment.service.InvestmentService;

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

    public InvestmentController(InvestmentService investmentService) {
        this.investmentService = investmentService;
    }

    @PostMapping
    public ResponseEntity<InvestmentResponse> submit(
            @Valid @RequestBody InvestmentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        String key = IdempotencyService.requireKey(idempotencyKey);
        var investment = investmentService.submit(SecurityUtils.currentUserId(), request, key);
        return ResponseEntity.ok(toResponse(investment));
    }

    @GetMapping
    public ResponseEntity<List<InvestmentResponse>> getOwn() {
        var investments = investmentService.getOwn(SecurityUtils.currentUserId());
        return ResponseEntity.ok(investments.stream().map(InvestmentController::toResponse).toList());
    }

    static InvestmentResponse toResponse(Investment i) {
        return InvestmentResponse.from(i);
    }
}
