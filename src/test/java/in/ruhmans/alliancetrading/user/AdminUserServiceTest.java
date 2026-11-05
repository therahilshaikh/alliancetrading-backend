package in.ruhmans.alliancetrading.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.auth.service.SessionService;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.investment.repository.InvestmentRepository;
import in.ruhmans.alliancetrading.ledger.repository.UserBalanceRepository;
import in.ruhmans.alliancetrading.payment.repository.PaymentAccountRepository;
import in.ruhmans.alliancetrading.payment.repository.UserPaymentAssignmentRepository;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;
import in.ruhmans.alliancetrading.user.service.AdminUserService;
import in.ruhmans.alliancetrading.user.service.ProfileService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class AdminUserServiceTest {

    private UserRepository userRepository;
    private SessionService sessionService;
    private AdminUserService adminUserService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        sessionService = Mockito.mock(SessionService.class);
        clock = Clock.fixed(Instant.now(), ZoneId.of("Asia/Kolkata"));

        adminUserService = new AdminUserService(userRepository, Mockito.mock(ProfileService.class),
                Mockito.mock(UserBalanceRepository.class), Mockito.mock(InvestmentRepository.class),
                Mockito.mock(UserPaymentAssignmentRepository.class), Mockito.mock(PaymentAccountRepository.class),
                sessionService, Mockito.mock(AuditService.class), clock);
    }

    private User userWithStatus(UserStatus status) {
        User user = new User("9876543210", clock.instant());
        user.setStatus(status);
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void suspendRevokesSessionsAndRemembersPreviousStatus() {
        User user = userWithStatus(UserStatus.APPROVED);

        adminUserService.suspend(user.getId(), java.util.UUID.randomUUID(), "Suspicious activity");

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(user.getSuspendedFromStatus()).isEqualTo(UserStatus.APPROVED);
        assertThat(user.getSuspensionReason()).isEqualTo("Suspicious activity");
        verify(sessionService).forceLogout(user.getId());
    }

    @Test
    void reactivateRestoresPreviousStatus() {
        User user = userWithStatus(UserStatus.KYC_SUBMITTED);
        adminUserService.suspend(user.getId(), java.util.UUID.randomUUID(), "Check documents");

        adminUserService.reactivate(user.getId(), java.util.UUID.randomUUID());

        assertThat(user.getStatus()).isEqualTo(UserStatus.KYC_SUBMITTED);
        assertThat(user.getSuspendedFromStatus()).isNull();
    }

    @Test
    void suspendRequiresReason() {
        User user = userWithStatus(UserStatus.APPROVED);

        assertThatThrownBy(() -> adminUserService.suspend(user.getId(), java.util.UUID.randomUUID(), " "))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void reactivateWhenNotSuspendedFails() {
        User user = userWithStatus(UserStatus.APPROVED);

        assertThatThrownBy(() -> adminUserService.reactivate(user.getId(), java.util.UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void searchEscapesLikeWildcardsAndLowercases() {
        when(userRepository.searchForAdmin(any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        adminUserService.search(" Arjun_100% ", null, 0, 500);

        var pattern = ArgumentCaptor.forClass(String.class);
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchForAdmin(any(), pattern.capture(), pageable.capture());
        assertThat(pattern.getValue()).isEqualTo("%arjun\\_100\\%%");
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void blankSearchMatchesEveryone() {
        when(userRepository.searchForAdmin(any(), eq("%"), any(Pageable.class))).thenReturn(Page.empty());

        adminUserService.search("", UserStatus.APPROVED, 0, 20);

        verify(userRepository).searchForAdmin(any(), eq("%"), any(Pageable.class));
    }
}
