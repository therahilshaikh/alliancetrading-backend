package in.ruhmans.alliancetrading.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import in.ruhmans.alliancetrading.user.service.UserDirectory;
import in.ruhmans.alliancetrading.withdrawal.api.dto.AdminWithdrawalResponse;
import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalRequest;
import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalStatus;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminWithdrawalResponseTest {

    private static WithdrawalRequest withdrawal(String gross, WithdrawalStatus status, UUID secondApprover) {
        WithdrawalRequest wr = Mockito.mock(WithdrawalRequest.class);
        Mockito.when(wr.getGrossAmount()).thenReturn(new BigDecimal(gross));
        Mockito.when(wr.getStatus()).thenReturn(status);
        Mockito.when(wr.getSecondApprovedBy()).thenReturn(secondApprover);
        Mockito.when(wr.getBankAccountLast4()).thenReturn("1234");
        return wr;
    }

    @Test
    void largeApprovedWithdrawalAwaitsSecondApproval() {
        var r = AdminWithdrawalResponse.from(withdrawal("60000", WithdrawalStatus.APPROVED, null),
                new UserDirectory.UserLabel(UUID.randomUUID(), "Ravi Kumar", "9876543210"), new BigDecimal("50000"));

        assertThat(r.secondApprovalRequired()).isTrue();
        assertThat(r.awaitingSecondApproval()).isTrue();
        assertThat(r.fullName()).isEqualTo("Ravi Kumar");
        assertThat(r.bankAccountMasked()).endsWith("1234");
    }

    @Test
    void secondApprovalNoLongerAwaitedOnceGiven() {
        var r = AdminWithdrawalResponse.from(withdrawal("60000", WithdrawalStatus.APPROVED, UUID.randomUUID()),
                null, new BigDecimal("50000"));

        assertThat(r.secondApprovalRequired()).isTrue();
        assertThat(r.awaitingSecondApproval()).isFalse();
    }

    @Test
    void smallWithdrawalNeedsOneApproval() {
        var r = AdminWithdrawalResponse.from(withdrawal("1000", WithdrawalStatus.APPROVED, null),
                null, new BigDecimal("50000"));

        assertThat(r.secondApprovalRequired()).isFalse();
        assertThat(r.awaitingSecondApproval()).isFalse();
    }
}
