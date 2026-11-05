package in.ruhmans.alliancetrading.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.Map;

import in.ruhmans.alliancetrading.notification.sms.Fast2SmsProperties;
import in.ruhmans.alliancetrading.notification.sms.Fast2SmsProperties.Route;
import in.ruhmans.alliancetrading.notification.sms.Fast2SmsSender;
import in.ruhmans.alliancetrading.notification.sms.SmsDeliveryException;
import in.ruhmans.alliancetrading.notification.sms.SmsMessage;
import in.ruhmans.alliancetrading.notification.sms.SmsTemplate;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class Fast2SmsSenderTest {

    private static final String URL = "https://fast2sms.test/dev/bulkV2";

    private static Fast2SmsProperties props(Route route, Map<String, String> templates) {
        return new Fast2SmsProperties("test-key", URL, route, "ALLNCE", templates,
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private record Harness(Fast2SmsSender sender, MockRestServiceServer server) {}

    private static Harness harness(Fast2SmsProperties props) {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL).defaultHeader("authorization", "test-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Harness(new Fast2SmsSender(props, builder.build()), server);
    }

    @Test
    void dltRouteSendsTemplateIdAndPipeJoinedVariables() {
        var h = harness(props(Route.DLT, Map.of("OTP", "171234")));
        h.server().expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("authorization", "test-key"))
                .andExpect(jsonPath("$.route").value("dlt"))
                .andExpect(jsonPath("$.sender_id").value("ALLNCE"))
                .andExpect(jsonPath("$.message").value("171234"))
                .andExpect(jsonPath("$.variables_values").value("482913|5"))
                .andExpect(jsonPath("$.numbers").value("9876543210"))
                .andRespond(withSuccess("{\"return\":true,\"request_id\":\"abc\"}", MediaType.APPLICATION_JSON));

        h.sender().send("9876543210", SmsMessage.of(SmsTemplate.OTP, "482913", "5"));

        h.server().verify();
    }

    @Test
    void otpRouteSendsOnlyTheCode() {
        var h = harness(props(Route.OTP, Map.of()));
        h.server().expect(requestTo(URL))
                .andExpect(jsonPath("$.route").value("otp"))
                .andExpect(jsonPath("$.variables_values").value("482913"))
                .andRespond(withSuccess("{\"return\":true}", MediaType.APPLICATION_JSON));

        h.sender().send("9876543210", SmsMessage.of(SmsTemplate.OTP, "482913", "5"));

        h.server().verify();
    }

    @Test
    void providerReturnFalseIsADeliveryFailure() {
        var h = harness(props(Route.DLT, Map.of("OTP", "171234")));
        h.server().expect(requestTo(URL))
                .andRespond(withSuccess("{\"return\":false,\"message\":\"Invalid template\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> h.sender().send("9876543210", SmsMessage.of(SmsTemplate.OTP, "1", "5")))
                .isInstanceOf(SmsDeliveryException.class)
                .hasMessageContaining("Invalid template");
    }

    @Test
    void httpErrorIsADeliveryFailure() {
        var h = harness(props(Route.DLT, Map.of("OTP", "171234")));
        h.server().expect(requestTo(URL)).andRespond(withBadRequest());

        assertThatThrownBy(() -> h.sender().send("9876543210", SmsMessage.of(SmsTemplate.OTP, "1", "5")))
                .isInstanceOf(SmsDeliveryException.class);
    }

    @Test
    void dltRouteWithoutOtpTemplateFailsAtStartup() {
        assertThatThrownBy(() -> harness(props(Route.DLT, Map.of("OTP", " "))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAST2SMS_TEMPLATE_OTP");
    }

    @Test
    void missingApiKeyFailsAtStartup() {
        var noKey = new Fast2SmsProperties(" ", URL, Route.OTP, null, Map.of(),
                Duration.ofSeconds(1), Duration.ofSeconds(1));
        assertThatThrownBy(() -> new Fast2SmsSender(noKey, RestClient.create()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAST2SMS_API_KEY");
    }

    @Test
    void templateVariableCountIsEnforced() {
        assertThat(SmsTemplate.OTP.variableCount()).isEqualTo(2);
        assertThatThrownBy(() -> SmsMessage.of(SmsTemplate.OTP, "123456"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otpIsTheOnlySmsTemplate() {
        assertThat(SmsTemplate.values()).containsExactly(SmsTemplate.OTP);
    }
}
