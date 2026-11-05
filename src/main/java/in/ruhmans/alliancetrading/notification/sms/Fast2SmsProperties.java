package in.ruhmans.alliancetrading.notification.sms;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Fast2SMS settings ({@code app.sms.fast2sms.*}).
 *
 * @param apiKey      account API key (from env {@code FAST2SMS_API_KEY}; never commit it)
 * @param url         bulk SMS endpoint
 * @param route       {@code dlt} (registered sender + templates, required for production
 *                    transactional SMS in India) or {@code otp} (Fast2SMS OTP route; OTPs only)
 * @param senderId    DLT-approved 6-character sender ID (dlt route)
 * @param templates   {@link SmsTemplate} name to the Fast2SMS DLT message/template ID (dlt route)
 */
@ConfigurationProperties(prefix = "app.sms.fast2sms")
public record Fast2SmsProperties(
        String apiKey,
        @DefaultValue("https://www.fast2sms.com/dev/bulkV2") String url,
        @DefaultValue("dlt") Route route,
        String senderId,
        @DefaultValue Map<String, String> templates,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("10s") Duration readTimeout) {

    public enum Route { DLT, OTP }
}
