package in.ruhmans.alliancetrading.notification.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Wires Fast2SMS when {@code app.sms.provider=fast2sms}; fails fast on missing config. */
@Configuration
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "fast2sms")
@EnableConfigurationProperties(Fast2SmsProperties.class)
public class Fast2SmsConfig {

    @Bean
    public SmsSender fast2SmsSender(Fast2SmsProperties props, RestClient.Builder restClientBuilder) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(props.connectTimeout());
        requestFactory.setReadTimeout(props.readTimeout());
        RestClient client = restClientBuilder
                .baseUrl(props.url())
                .requestFactory(requestFactory)
                .defaultHeader("authorization", props.apiKey() != null ? props.apiKey() : "")
                .build();
        return new Fast2SmsSender(props, client);
    }
}
