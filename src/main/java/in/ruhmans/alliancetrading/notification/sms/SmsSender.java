package in.ruhmans.alliancetrading.notification.sms;

/**
 * Sends a templated SMS. Implementations throw {@link SmsDeliveryException} when the
 * provider does not accept the message, so callers can retry or surface the failure.
 */
public interface SmsSender {

    void send(String mobile, SmsMessage message);
}
