package in.ruhmans.alliancetrading.bank.api;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import in.ruhmans.alliancetrading.bank.api.dto.AdminBankAccountResponse;
import in.ruhmans.alliancetrading.bank.api.dto.BankRejectRequest;
import in.ruhmans.alliancetrading.bank.domain.BankAccount;
import in.ruhmans.alliancetrading.bank.domain.BankAccountStatus;
import in.ruhmans.alliancetrading.bank.service.BankAccountService;
import in.ruhmans.alliancetrading.common.pagination.PageResponse;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
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
@RequestMapping("/api/v1/admin/bank-accounts")
public class AdminBankAccountController {

    private final BankAccountService bankAccountService;
    private final UserDirectory userDirectory;

    public AdminBankAccountController(BankAccountService bankAccountService, UserDirectory userDirectory) {
        this.bankAccountService = bankAccountService;
        this.userDirectory = userDirectory;
    }

    /** Newest first. */
    @GetMapping
    public ResponseEntity<PageResponse<AdminBankAccountResponse>> list(
            @RequestParam(defaultValue = "PENDING_VERIFICATION") BankAccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var accounts = bankAccountService.listByStatus(status, PageResponse.newestFirst(page, size));
        return ResponseEntity.ok(PageResponse.of(accounts, toResponses(accounts.getContent())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminBankAccountResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponses(List.of(bankAccountService.getById(id))).getFirst());
    }

    private List<AdminBankAccountResponse> toResponses(List<BankAccount> accounts) {
        var labels = userDirectory.labels(accounts.stream().map(BankAccount::getUserId)
                .collect(Collectors.toSet()));
        return accounts.stream().map(a -> AdminBankAccountResponse.from(a, labels.get(a.getUserId()))).toList();
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<Void> verify(@PathVariable UUID id) {
        bankAccountService.verify(id, SecurityUtils.currentAdminId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id,
                                       @Valid @RequestBody BankRejectRequest request) {
        bankAccountService.reject(id, SecurityUtils.currentAdminId(), request.reason());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/account-number")
    public ResponseEntity<AccountNumberView> getAccountNumber(@PathVariable UUID id) {
        String accountNumber = bankAccountService.getDecryptedAccountNumber(
                id, SecurityUtils.currentAdminId());
        return ResponseEntity.ok(new AccountNumberView(accountNumber));
    }

    public record AccountNumberView(String accountNumber) {}
}
