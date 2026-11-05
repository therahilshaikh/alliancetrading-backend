package in.ruhmans.alliancetrading.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.notification.domain.OutboxMessage;
import in.ruhmans.alliancetrading.notification.domain.UserDevice;
import in.ruhmans.alliancetrading.notification.repository.OutboxRepository;
import in.ruhmans.alliancetrading.notification.repository.UserDeviceRepository;
import in.ruhmans.alliancetrading.notification.service.OutboxDispatcher;
import in.ruhmans.alliancetrading.notification.service.PushSender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OutboxDispatcherTest {

    private OutboxRepository outboxRepository;
    private UserDeviceRepository userDeviceRepository;
    private PushSender pushSender;
    private OutboxDispatcher dispatcher;
    private Clock clock;

    @BeforeEach
    void setUp() {
        outboxRepository = Mockito.mock(OutboxRepository.class);
        userDeviceRepository = Mockito.mock(UserDeviceRepository.class);
        pushSender = Mockito.mock(PushSender.class);
        clock = Clock.fixed(Instant.now(), ZoneId.of("Asia/Kolkata"));

        dispatcher = new OutboxDispatcher(outboxRepository, userDeviceRepository, pushSender, clock);
    }

    private OutboxMessage pending(String eventType, UUID userId, String channel) {
        OutboxMessage msg = new OutboxMessage(eventType, "USER", userId, channel, "{}", clock.instant());
        when(outboxRepository.findPendingBatch(any(), any(Integer.class))).thenReturn(List.of(msg));
        return msg;
    }

    private void device(UUID userId) {
        when(userDeviceRepository.findById(userId))
                .thenReturn(Optional.of(new UserDevice(userId, "fcm-token-123", clock.instant())));
    }

    @Test
    void dispatchesPushWithTheEventText() {
        UUID userId = UUID.randomUUID();
        OutboxMessage msg = pending("WITHDRAWAL_PAID", userId, "PUSH");
        device(userId);

        dispatcher.dispatch();

        assertThat(msg.getStatus()).isEqualTo("SENT");
        verify(pushSender).send(eq("fcm-token-123"), eq("WITHDRAWAL_PAID"),
                eq("Your withdrawal has been paid to your registered bank account."));
    }

    @Test
    void leftoverSmsRowsAreDroppedNotSent() {
        UUID userId = UUID.randomUUID();
        OutboxMessage msg = pending("KYC_APPROVED", userId, "SMS");
        device(userId);

        dispatcher.dispatch();

        assertThat(msg.getStatus()).isEqualTo("SENT");
        verify(pushSender, never()).send(any(), any(), any());
    }

    @Test
    void pushWithoutRegisteredDeviceIsSkippedNotRetried() {
        UUID userId = UUID.randomUUID();
        OutboxMessage msg = pending("PNL_POSTED", userId, "PUSH");
        when(userDeviceRepository.findById(userId)).thenReturn(Optional.empty());

        dispatcher.dispatch();

        assertThat(msg.getStatus()).isEqualTo("SENT");
        verify(pushSender, never()).send(any(), any(), any());
    }

    @Test
    void failedDispatchSchedulesRetry() {
        UUID userId = UUID.randomUUID();
        OutboxMessage msg = pending("KYC_APPROVED", userId, "PUSH");
        device(userId);
        Mockito.doThrow(new RuntimeException("push provider down")).when(pushSender).send(any(), any(), any());

        dispatcher.dispatch();

        assertThat(msg.getStatus()).isEqualTo("PENDING");
        assertThat(msg.getAttempts()).isEqualTo(1);
        assertThat(msg.getNextAttemptAt()).isAfter(clock.instant());
    }

    @Test
    void marksFailedAfterMaxAttempts() {
        UUID userId = UUID.randomUUID();
        OutboxMessage msg = pending("KYC_APPROVED", userId, "PUSH");
        for (int i = 0; i < 9; i++) {
            msg.markFailed(clock.instant().plusSeconds(60));
        }
        device(userId);
        Mockito.doThrow(new RuntimeException("push provider down")).when(pushSender).send(any(), any(), any());

        dispatcher.dispatch();

        assertThat(msg.getStatus()).isEqualTo("FAILED");
    }
}
