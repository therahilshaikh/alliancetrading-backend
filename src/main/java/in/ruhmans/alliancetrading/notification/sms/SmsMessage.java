package in.ruhmans.alliancetrading.notification.sms;

import java.util.List;

/** A template plus its ordered DLT variables. */
public record SmsMessage(SmsTemplate template, List<String> variables) {

    public SmsMessage {
        variables = List.copyOf(variables);
        if (variables.size() != template.variableCount()) {
            throw new IllegalArgumentException(template + " expects " + template.variableCount()
                    + " variables, got " + variables.size());
        }
    }

    public static SmsMessage of(SmsTemplate template, String... variables) {
        return new SmsMessage(template, List.of(variables));
    }

    /** The full message text as the recipient will see it. */
    public String text() {
        return template.render(variables.toArray());
    }
}
