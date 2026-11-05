package in.ruhmans.alliancetrading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AlliancetradingApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlliancetradingApplication.class, args);
    }
}
