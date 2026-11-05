package in.ruhmans.alliancetrading.support.repository;

import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.support.domain.SupportMessage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {

    List<SupportMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
