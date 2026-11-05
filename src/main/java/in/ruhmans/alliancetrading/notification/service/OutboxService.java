package in.ruhmans.alliancetrading.notification.service;

import java.time.Clock;
import java.util.UUID;

import in.ruhmans.alliancetrading.notification.domain.OutboxMessage;
import in.ruhmans.alliancetrading.notification.repository.OutboxRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox: enqueue within the same transaction as the business change.
 * The dispatcher (Phase 8) drains PENDING rows with exponential backoff.
 */
@Service
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final Clock clock;

    public OutboxService(OutboxRepository outboxRepository, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void enqueue(String eventType, String recipientType, UUID recipientId,
                        String channel, String jsonPayload) {
        outboxRepository.save(new OutboxMessage(eventType, recipientType, recipientId,
                channel, jsonPayload, clock.instant()));
    }

    /** Account events are delivered by push only; SMS is reserved for the login OTP. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void notifyUser(String eventType, UUID userId, String jsonPayload) {
        enqueue(eventType, "USER", userId, "PUSH", jsonPayload);
    }
}
