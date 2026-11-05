package in.ruhmans.alliancetrading.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.business-timezone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
