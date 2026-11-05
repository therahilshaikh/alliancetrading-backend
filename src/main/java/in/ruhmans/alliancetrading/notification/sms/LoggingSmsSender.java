package in.ruhmans.alliancetrading.notification.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local development sender: writes the SMS to the log (including OTP codes) instead of
 * sending it. Active only when {@code app.sms.provider=logging}, the default.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "logging", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void send(String mobile, SmsMessage message) {
        log.info("SMS [{}] to {}: {}", message.template(), mobile, message.text());
    }
}
