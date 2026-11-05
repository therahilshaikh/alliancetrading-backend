package in.ruhmans.alliancetrading.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        String bearerScheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("AllianceTrading API")
                        .version("v1")
                        .description("""
                                Record-keeping backend for AllianceTrading.

                                ## Authentication
                                - **Users**: OTP via `/auth/otp/request` then `/auth/otp/verify` → access + refresh tokens.
                                - **Admins**: `/admin/auth/login` (email + password) → access + refresh tokens.

                                ## Error format
                                All errors are RFC 7807 ProblemDetail with a stable `code` field, e.g.
                                `VALIDATION_FAILED`, `SESSION_REPLACED`, `INSUFFICIENT_WITHDRAWABLE`,
                                `ILLEGAL_STATE_TRANSITION`, `MAKER_CHECKER_VIOLATION`, `INVALID_APPROVER`,
                                `DUPLICATE_PAN`, `DUPLICATE_AADHAAR`, `NO_PAYMENT_ASSIGNMENT`, `SMS_SEND_FAILED`,
                                `IDEMPOTENCY_CONFLICT`, `FEATURE_DISABLED`, `QUOTE_EXPIRED`, `RATE_LIMITED`.

                                ## Idempotency
                                Every money-moving POST requires an `Idempotency-Key` header.
                                Replays with the same key return the original response.

                                ## Roles
                                `USER` (mobile), `ADMIN` and `SUPER_ADMIN` (web). Maker-checker:
                                approver must differ from creator on settings; P&L batches can only be
                                approved by the admin the maker assigned.
                                """)
                        .contact(new Contact().name("AllianceTrading Engineering")))
                .addServersItem(new Server().url("/").description("Default"))
                .addSecurityItem(new SecurityRequirement().addList(bearerScheme))
                .schemaRequirement(bearerScheme, new SecurityScheme()
                        .name(bearerScheme)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));
    }
}
