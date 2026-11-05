package in.ruhmans.alliancetrading.notification.sms;

/**
 * Every SMS the platform sends. SMS is used only for the login OTP; account notifications go by push.
 * Indian DLT rules require each SMS to match a template pre-registered with the operator, so messages
 * are identified by template, not free text. The {@code text} here is the registered wording; {@code %s}
 * placeholders are the DLT variables, filled in order. Keep it identical to what is registered on Fast2SMS.
 */
public enum SmsTemplate {

    OTP("Your AllianceTrading OTP is %s. It is valid for %s minutes. Do not share it with anyone.");

    private final String text;

    SmsTemplate(String text) {
        this.text = text;
    }

    public String render(Object... variables) {
        return String.format(text, variables);
    }

    public int variableCount() {
        return text.split("%s", -1).length - 1;
    }
}
