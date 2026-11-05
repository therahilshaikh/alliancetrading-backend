package in.ruhmans.alliancetrading.withdrawal.repository;

import java.util.UUID;

import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalQuote;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalQuoteRepository extends JpaRepository<WithdrawalQuote, UUID> {
}
