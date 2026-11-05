package in.ruhmans.alliancetrading.common.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import in.ruhmans.alliancetrading.admin.domain.AdminRole;
import in.ruhmans.alliancetrading.auth.service.AdminAuthService;
import in.ruhmans.alliancetrading.payment.domain.PaymentAccount;
import in.ruhmans.alliancetrading.payment.repository.PaymentAccountRepository;
import in.ruhmans.alliancetrading.settings.domain.AppSetting;
import in.ruhmans.alliancetrading.settings.domain.FeeSetting;
import in.ruhmans.alliancetrading.settings.domain.PaymentSetting;
import in.ruhmans.alliancetrading.settings.domain.WithdrawalType;
import in.ruhmans.alliancetrading.settings.repository.AppSettingRepository;
import in.ruhmans.alliancetrading.settings.repository.FeeSettingRepository;
import in.ruhmans.alliancetrading.settings.repository.PaymentSettingRepository;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev"})
@Order(1)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AdminAuthService adminAuthService;
    private final PaymentSettingRepository paymentSettingRepository;
    private final FeeSettingRepository feeSettingRepository;
    private final AppSettingRepository appSettingRepository;
    private final PaymentAccountRepository paymentAccountRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public DataSeeder(AdminAuthService adminAuthService,
                      PaymentSettingRepository paymentSettingRepository,
                      FeeSettingRepository feeSettingRepository,
                      AppSettingRepository appSettingRepository,
                      PaymentAccountRepository paymentAccountRepository,
                      UserRepository userRepository, Clock clock) {
        this.adminAuthService = adminAuthService;
        this.paymentSettingRepository = paymentSettingRepository;
        this.feeSettingRepository = feeSettingRepository;
        this.appSettingRepository = appSettingRepository;
        this.paymentAccountRepository = paymentAccountRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByMobile("9876543210").isPresent()) {
            log.info("Local seed data already present; skipping.");
            return;
        }
        log.info("Seeding local development data...");

        // Admins
        var superAdmin = adminAuthService.createAdmin("super@alliancetrading.local",
                "SuperSecure1!", AdminRole.SUPER_ADMIN, "Super Admin");
        var admin = adminAuthService.createAdmin("admin@alliancetrading.local",
                "AdminSecure1!", AdminRole.ADMIN, "Ops Admin");
        log.info("Created SUPER_ADMIN super@alliancetrading.local");
        log.info("Created ADMIN admin@alliancetrading.local");

        // Second super admin, for testing settings and admin management with two accounts
        adminAuthService.createAdmin("super2@alliancetrading.local",
                "SuperSecure2!", AdminRole.SUPER_ADMIN, "Super Admin 2");
        // Saved company payment account, selectable when approving members, and the default payment pointing to it
        var companyAccount = paymentAccountRepository.save(new PaymentAccount("Company UPI (sample)",
                "alliancetrading@upi", "AllianceTrading", null, superAdmin.getId(), Instant.now()));
        paymentSettingRepository.save(new PaymentSetting(new BigDecimal("50000.00"), companyAccount.getId(),
                Instant.now(), superAdmin.getId(), Instant.now()));
        log.info("Seeded payment account alliancetrading@upi as the default payment (50,000)");

        // Fee settings
        var profitFee = new FeeSetting(WithdrawalType.PROFIT, new BigDecimal("0.0200"),
                Instant.now(), superAdmin.getId(), Instant.now());
        feeSettingRepository.save(profitFee);

        var principalFee = new FeeSetting(WithdrawalType.PRINCIPAL, new BigDecimal("0.0500"),
                Instant.now(), superAdmin.getId(), Instant.now());
        feeSettingRepository.save(principalFee);
        log.info("Seeded fee settings: 2% profit, 5% principal");

        // App settings
        seedAppSetting(AppSetting.MIN_PROFIT_WITHDRAWAL, "500", superAdmin);
        seedAppSetting(AppSetting.PRINCIPAL_WITHDRAWAL_ENABLED, "false", superAdmin);
        seedAppSetting(AppSetting.BANK_CHANGE_COOLOFF_HOURS, "48", superAdmin);
        seedAppSetting(AppSetting.SECOND_APPROVAL_THRESHOLD, "50000", superAdmin);
        seedAppSetting(AppSetting.MIN_APP_VERSION, "1.0.0", superAdmin);

        // Fake users
        userRepository.save(new User("9876543210", clock.instant()));
        userRepository.save(new User("9876543211", clock.instant()));
        userRepository.save(new User("9876543212", clock.instant()));
        log.info("Seeded 3 test users (9876543210-9876543212)");

        log.info("Local data seeding complete.");
    }

    private void seedAppSetting(String key, String value,
                                in.ruhmans.alliancetrading.admin.domain.AdminUser creator) {
        appSettingRepository.save(new AppSetting(key, value, Instant.now(), creator.getId(), Instant.now()));
    }
}
