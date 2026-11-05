package in.ruhmans.alliancetrading.payment.repository;

import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.payment.domain.PaymentAccount;
import in.ruhmans.alliancetrading.payment.domain.PaymentAccountStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentAccountRepository extends JpaRepository<PaymentAccount, UUID> {

    List<PaymentAccount> findByStatusOrderByLabelAsc(PaymentAccountStatus status);

    @Query("SELECT COUNT(a) > 0 FROM PaymentAccount a "
            + "WHERE a.status = 'ACTIVE' AND lower(a.vpa) = lower(:vpa)")
    boolean existsActiveByVpa(String vpa);
}
