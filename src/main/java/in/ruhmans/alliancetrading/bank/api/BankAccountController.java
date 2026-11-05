package in.ruhmans.alliancetrading.bank.api;

import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.bank.api.dto.BankAccountRequest;
import in.ruhmans.alliancetrading.bank.api.dto.BankAccountResponse;
import in.ruhmans.alliancetrading.bank.domain.BankAccount;
import in.ruhmans.alliancetrading.bank.service.BankAccountService;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bank-accounts")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    public BankAccountController(BankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    @PostMapping
    public ResponseEntity<BankAccountResponse> submit(@Valid @RequestBody BankAccountRequest request) {
        var account = bankAccountService.submit(SecurityUtils.currentUserId(), request);
        return ResponseEntity.ok(toResponse(account));
    }

    @GetMapping
    public ResponseEntity<List<BankAccountResponse>> getOwn() {
        var accounts = bankAccountService.getOwn(SecurityUtils.currentUserId());
        return ResponseEntity.ok(accounts.stream().map(BankAccountController::toResponse).toList());
    }

    static BankAccountResponse toResponse(BankAccount a) {
        return BankAccountResponse.from(a);
    }
}
