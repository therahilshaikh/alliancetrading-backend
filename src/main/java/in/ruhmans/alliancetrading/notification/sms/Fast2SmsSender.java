package in.ruhmans.alliancetrading.notification.sms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends SMS through Fast2SMS's bulk API.
 *
 * <ul>
 *   <li>{@code DLT} route: DLT-registered sender ID + template ID; variables are joined with
 *       {@code |} in template order. Use this in production.</li>
 *   <li>{@code OTP} route: Fast2SMS's OTP route; only the OTP template is supported, other
 *       notifications are skipped (they still go out as push).</li>
 * </ul>
 *
 * All Fast2SMS-specific request/response details live in this class. Verify them against
 * the Fast2SMS dashboard docs for your account before going live.
 */
public class Fast2SmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(Fast2SmsSender.class);

    private final Fast2SmsProperties props;
    private final RestClient client;

    /**
     * @param client pre-configured with the Fast2SMS base URL, auth header and timeouts
     *               (see {@link Fast2SmsConfig})
     */
    public Fast2SmsSender(Fast2SmsProperties props, RestClient client) {
        validate(props);
        this.props = props;
        this.client = client;
    }

    @Override
    public void send(String mobile, SmsMessage message) {
        Map<String, Object> body = buildRequest(mobile, message);
        if (body == null) {
            log.warn("SMS {} skipped: no Fast2SMS template for it on route {}", message.template(), props.route());
            return;
        }

        Map<?, ?> response;
        try {
            response = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw new SmsDeliveryException("Fast2SMS request failed: " + e.getMessage(), e);
        }
        if (response == null || !Boolean.TRUE.equals(response.get("return"))) {
            throw new SmsDeliveryException("Fast2SMS rejected " + message.template() + ": "
                    + (response != null ? response.get("message") : "empty response"));
        }
        log.info("SMS {} sent to {} (request_id={})", message.template(), mask(mobile), response.get("request_id"));
    }

    /** Fast2SMS request body, or null when the route cannot carry this template. */
    Map<String, Object> buildRequest(String mobile, SmsMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        switch (props.route()) {
            case OTP -> {
                if (message.template() != SmsTemplate.OTP) {
                    return null;
                }
                body.put("route", "otp");
                body.put("variables_values", message.variables().getFirst());
            }
            case DLT -> {
                // Templates not yet registered are skipped (OTP is required at startup)
                String templateId = props.templates().get(message.template().name());
                if (templateId == null || templateId.isBlank()) {
                    return null;
                }
                body.put("route", "dlt");
                body.put("sender_id", props.senderId());
                body.put("message", templateId);
                body.put("variables_values", String.join("|", message.variables()));
                body.put("flash", 0);
            }
        }
        body.put("numbers", mobile);
        return body;
    }

    private static void validate(Fast2SmsProperties props) {
        List<String> problems = new ArrayList<>();
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            problems.add("app.sms.fast2sms.api-key (FAST2SMS_API_KEY) is required");
        }
        if (props.route() == Fast2SmsProperties.Route.DLT) {
            if (props.senderId() == null || props.senderId().isBlank()) {
                problems.add("app.sms.fast2sms.sender-id is required for the DLT route");
            }
            String otpTemplate = props.templates().get(SmsTemplate.OTP.name());
            if (otpTemplate == null || otpTemplate.isBlank()) {
                problems.add("app.sms.fast2sms.templates.OTP (FAST2SMS_TEMPLATE_OTP) is required for the DLT route");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid Fast2SMS configuration: " + String.join("; ", problems));
        }
    }

    private static String mask(String mobile) {
        return mobile.length() <= 4 ? "****" : "******" + mobile.substring(mobile.length() - 4);
    }
}
