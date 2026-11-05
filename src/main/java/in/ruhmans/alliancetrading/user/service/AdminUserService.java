package in.ruhmans.alliancetrading.user.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.auth.service.SessionService;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.common.pagination.PageResponse;
import in.ruhmans.alliancetrading.investment.api.dto.InvestmentResponse;
import in.ruhmans.alliancetrading.investment.repository.InvestmentRepository;
import in.ruhmans.alliancetrading.ledger.repository.UserBalanceRepository;
import in.ruhmans.alliancetrading.payment.api.dto.PaymentAssignmentResponse;
import in.ruhmans.alliancetrading.payment.repository.PaymentAccountRepository;
import in.ruhmans.alliancetrading.payment.repository.UserPaymentAssignmentRepository;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.AdminUserRow;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin member management: search, detail view, suspend and reactivate. */
@Service
public class AdminUserService {

    public record Balances(BigDecimal principal, BigDecimal profit, BigDecimal held, BigDecimal currentValue) {}

    public record UserDetail(ProfileService.Profile profile, Balances balances,
                             List<InvestmentResponse> investments,
                             PaymentAssignmentResponse paymentAssignment,
                             UserStatus suspendedFromStatus, Instant suspendedAt, String suspensionReason) {}

    private final UserRepository userRepository;
    private final ProfileService profileService;
    private final UserBalanceRepository userBalanceRepository;
    private final InvestmentRepository investmentRepository;
    private final UserPaymentAssignmentRepository assignmentRepository;
    private final PaymentAccountRepository paymentAccountRepository;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final Clock clock;

    public AdminUserService(UserRepository userRepository, ProfileService profileService,
                            UserBalanceRepository userBalanceRepository, InvestmentRepository investmentRepository,
                            UserPaymentAssignmentRepository assignmentRepository,
                            PaymentAccountRepository paymentAccountRepository, SessionService sessionService,
                            AuditService auditService, Clock clock) {
        this.userRepository = userRepository;
        this.profileService = profileService;
        this.userBalanceRepository = userBalanceRepository;
        this.investmentRepository = investmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.paymentAccountRepository = paymentAccountRepository;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * @param search matches name or mobile, case-insensitive; blank for all
     * @param status a single status, or null for every status
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserRow> search(String search, UserStatus status, int page, int size) {
        var statuses = status != null ? EnumSet.of(status) : EnumSet.allOf(UserStatus.class);
        String pattern = search == null || search.isBlank()
                ? "%"
                : "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
        var rows = userRepository.searchForAdmin(statuses, pattern,
                PageRequest.of(Math.max(page, 0), PageResponse.clampSize(size)));
        return PageResponse.of(rows, r -> r);
    }

    @Transactional(readOnly = true)
    public UserDetail detail(UUID userId) {
        User user = requireUser(userId);
        var balance = userBalanceRepository.findById(userId).orElse(null);
        var balances = balance == null
                ? new Balances(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                : new Balances(balance.getPrincipal(), balance.getProfit(), balance.getHeld(), balance.currentValue());
        var investments = investmentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(InvestmentResponse::from).toList();
        var assignment = assignmentRepository.findByUserIdAndActiveTrue(userId)
                .map(a -> PaymentAssignmentResponse.from(a,
                        paymentAccountRepository.findById(a.getPaymentAccountId()).orElseThrow()))
                .orElse(null);
        return new UserDetail(profileService.profile(userId), balances, investments, assignment,
                user.getSuspendedFromStatus(), user.getSuspendedAt(), user.getSuspensionReason());
    }

    /** Blocks the member: revokes their sessions and prevents new logins until reactivated. */
    @Transactional
    public void suspend(UUID userId, UUID adminId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "A reason is required to suspend a user");
        }
        Instant now = clock.instant();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        if (user.isSuspended()) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION, "User is already suspended");
        }
        UserStatus previous = user.getStatus();
        user.suspend(reason.trim(), now);
        userRepository.save(user);
        sessionService.forceLogout(userId);
        auditService.recordAdmin(adminId, "USER_SUSPENDED", "user", userId.toString(),
                AuditService.json("status", previous), AuditService.json("status", UserStatus.SUSPENDED, "reason", reason));
    }

    @Transactional
    public void reactivate(UUID userId, UUID adminId) {
        Instant now = clock.instant();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        if (!user.isSuspended()) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION, "User is not suspended");
        }
        user.reactivate(now);
        userRepository.save(user);
        auditService.recordAdmin(adminId, "USER_REACTIVATED", "user", userId.toString(),
                AuditService.json("status", UserStatus.SUSPENDED), AuditService.json("status", user.getStatus()));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
    }

    /** Treat user input literally inside a LIKE pattern. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
