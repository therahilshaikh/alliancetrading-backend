package in.ruhmans.alliancetrading.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.ruhmans.alliancetrading.audit.service.AuditService;

import org.junit.jupiter.api.Test;

class AuditJsonTest {

    @Test
    void freeTextWithQuotesProducesValidJson() throws Exception {
        String json = AuditService.json("reason", "Payment \"not\" received\\", "amount", 10);

        var parsed = new ObjectMapper().readTree(json);
        assertThat(parsed.get("reason").asText()).isEqualTo("Payment \"not\" received\\");
        assertThat(parsed.get("amount").asInt()).isEqualTo(10);
    }

    @Test
    void oddArgumentCountIsRejected() {
        assertThatThrownBy(() -> AuditService.json("reason"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
