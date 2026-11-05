package in.ruhmans.alliancetrading.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.push.provider", havingValue = "logging", matchIfMissing = true)
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public void send(String pushToken, String title, String body) {
        log.info("PUSH to {}: {} — {}", pushToken, title, body);
    }
}
