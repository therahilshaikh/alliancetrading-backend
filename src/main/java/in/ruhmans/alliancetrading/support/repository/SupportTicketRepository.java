package in.ruhmans.alliancetrading.support.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.support.domain.SupportTicket;
import in.ruhmans.alliancetrading.support.domain.TicketPriority;
import in.ruhmans.alliancetrading.support.domain.TicketStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    List<SupportTicket> findByUserIdOrderByLastMessageAtDesc(UUID userId);

    /** Admin queue: high priority first, then the longest-waiting conversation. */
    @Query("SELECT t FROM SupportTicket t WHERE t.status IN :statuses AND t.priority IN :priorities "
            + "ORDER BY CASE WHEN t.priority = 'HIGH' THEN 0 ELSE 1 END, t.lastMessageAt ASC")
    Page<SupportTicket> findQueue(Collection<TicketStatus> statuses, Collection<TicketPriority> priorities,
                                  Pageable pageable);

    long countByStatus(TicketStatus status);
}
