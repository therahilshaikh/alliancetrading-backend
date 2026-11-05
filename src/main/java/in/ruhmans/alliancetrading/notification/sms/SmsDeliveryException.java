package in.ruhmans.alliancetrading.notification.sms;

/** The SMS provider rejected the message or could not be reached. */
public class SmsDeliveryException extends RuntimeException {

    public SmsDeliveryException(String message) {
        super(message);
    }

    public SmsDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
