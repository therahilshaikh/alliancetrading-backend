package in.ruhmans.alliancetrading.notification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import in.ruhmans.alliancetrading.notification.domain.OutboxMessage;
import in.ruhmans.alliancetrading.notification.repository.OutboxRepository;
import in.ruhmans.alliancetrading.notification.repository.UserDeviceRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers queued account notifications as push messages. SMS is used only for the login OTP, which
 * {@code OtpService} sends directly; nothing in the outbox goes out by SMS.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int BATCH_SIZE = 50;
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    /** Push text per event; unknown events fall back to the event name. */
    static final Map<String, String> PUSH_TEXT = Map.ofEntries(
            Map.entry("KYC_APPROVED", "Your account has been approved. Open the app to make your investment."),
            Map.entry("KYC_REJECTED", "Your account application needs changes. Open the app to review and resubmit."),
            Map.entry("INVESTMENT_APPROVED", "Your investment has been verified and is now active."),
            Map.entry("INVESTMENT_REJECTED", "Your payment could not be verified. Open the app or contact support."),
            Map.entry("WITHDRAWAL_REQUESTED", "Your withdrawal request has been received and is under review."),
            Map.entry("WITHDRAWAL_PAID", "Your withdrawal has been paid to your registered bank account."),
            Map.entry("WITHDRAWAL_REJECTED", "Your withdrawal request was declined. Open the app for details."),
            Map.entry("WITHDRAWAL_PAYOUT_FAILED", "Your withdrawal payout could not be completed. We will retry or contact you."),
            Map.entry("PNL_POSTED", "Today's profit/loss has been posted to your account."),
            Map.entry("BANK_VERIFIED", "Your bank account has been verified."),
            Map.entry("SUPPORT_REPLY", "Support has replied to your query."));

    private final OutboxRepository outboxRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final PushSender pushSender;
    private final Clock clock;

    public OutboxDispatcher(OutboxRepository outboxRepository, UserDeviceRepository userDeviceRepository,
                            PushSender pushSender, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.pushSender = pushSender;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void dispatch() {
        List<OutboxMessage> batch = outboxRepository.findPendingBatch(clock.instant(), BATCH_SIZE);
        for (OutboxMessage msg : batch) {
            try {
                send(msg);
                msg.markSent(clock.instant());
            } catch (Exception e) {
                log.warn("Outbox dispatch failed for id={} channel={}: {}",
                        msg.getId(), msg.getChannel(), e.getMessage());
                msg.markFailed(nextRetry(msg.getAttempts()));
            }
            outboxRepository.save(msg);
        }
    }

    private void send(OutboxMessage msg) {
        if (!"PUSH".equals(msg.getChannel())) {
            // Older SMS rows (queued before account SMS was switched off) are dropped, never sent
            log.info("Outbox id={} on channel {} skipped: only PUSH is delivered", msg.getId(), msg.getChannel());
            return;
        }
        var token = resolvePushToken(msg);
        if (token == null) {
            // User has not installed/registered the app yet; nothing to deliver
            log.debug("No push token for user {}; outbox id={} skipped", msg.getRecipientId(), msg.getId());
            return;
        }
        pushSender.send(token, msg.getEventType(), PUSH_TEXT.getOrDefault(msg.getEventType(), msg.getEventType()));
    }

    /** The user's registered push token, or null if they have none. */
    private String resolvePushToken(OutboxMessage msg) {
        if (!"USER".equals(msg.getRecipientType())) {
            throw new IllegalStateException("PUSH only for USER recipients");
        }
        return userDeviceRepository.findById(msg.getRecipientId())
                .map(d -> d.getPushToken())
                .orElse(null);
    }

    private Instant nextRetry(int attempts) {
        long delayMs = Math.min(MAX_BACKOFF.toMillis(), (long) Math.pow(2, attempts) * 60_000);
        return clock.instant().plusMillis(delayMs);
    }
}
