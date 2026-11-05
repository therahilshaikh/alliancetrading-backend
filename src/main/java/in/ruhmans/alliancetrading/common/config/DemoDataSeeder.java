package in.ruhmans.alliancetrading.common.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.admin.domain.AdminUser;
import in.ruhmans.alliancetrading.admin.repository.AdminUserRepository;
import in.ruhmans.alliancetrading.bank.api.dto.BankAccountRequest;
import in.ruhmans.alliancetrading.bank.domain.BankAccountType;
import in.ruhmans.alliancetrading.bank.service.BankAccountService;
import in.ruhmans.alliancetrading.files.domain.DocType;
import in.ruhmans.alliancetrading.files.service.FileService;
import in.ruhmans.alliancetrading.investment.api.dto.InvestmentRequest;
import in.ruhmans.alliancetrading.investment.domain.Investment;
import in.ruhmans.alliancetrading.investment.service.InvestmentService;
import in.ruhmans.alliancetrading.kyc.api.dto.KycSubmitRequest;
import in.ruhmans.alliancetrading.kyc.service.AadhaarValidator;
import in.ruhmans.alliancetrading.kyc.service.AccountApprovalService;
import in.ruhmans.alliancetrading.kyc.service.KycService;
import in.ruhmans.alliancetrading.payment.domain.PaymentAccountStatus;
import in.ruhmans.alliancetrading.payment.repository.PaymentAccountRepository;
import in.ruhmans.alliancetrading.payment.service.PaymentService;
import in.ruhmans.alliancetrading.pnl.domain.PnlBatch;
import in.ruhmans.alliancetrading.pnl.domain.PnlBatchType;
import in.ruhmans.alliancetrading.pnl.domain.PnlTargetType;
import in.ruhmans.alliancetrading.pnl.domain.PnlValueType;
import in.ruhmans.alliancetrading.pnl.service.PnlService;
import in.ruhmans.alliancetrading.settings.domain.WithdrawalType;
import in.ruhmans.alliancetrading.support.service.SupportService;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.repository.UserRepository;
import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalRequest;
import in.ruhmans.alliancetrading.withdrawal.service.WithdrawalService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Local/dev demo data built through the real services, so the ledger, balances, audit log and
 * queues are consistent. Runs after {@link DataSeeder} and skips itself once the demo members exist.
 * Payment proofs are small generated receipt images uploaded through {@link FileService}, so they are
 * stored (encrypted) under the normal key layout and open in the console like real uploads.
 */
@Component
@Profile({"local", "dev"})
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String MARKER_MOBILE = "9000000101";

    private record Member(String mobile, String name, String dob, Stage stage, String amount) {}

    private enum Stage { REGISTERED, KYC_SUBMITTED, REJECTED, PENDING_INVESTMENT, ACTIVE }

    private static final List<Member> MEMBERS = List.of(
            new Member("9000000101", "Aarav Sharma", "1990-03-14", Stage.ACTIVE, "10000"),
            new Member("9000000102", "Priya Patel", "1988-07-22", Stage.ACTIVE, "50000"),
            new Member("9000000103", "Rohan Gupta", "1993-11-05", Stage.ACTIVE, "50000"),
            new Member("9000000104", "Ananya Iyer", "1996-01-30", Stage.ACTIVE, "10000"),
            new Member("9000000105", "Vikram Singh", "1985-09-18", Stage.PENDING_INVESTMENT, "10000"),
            new Member("9000000106", "Sneha Reddy", "1998-04-09", Stage.KYC_SUBMITTED, null),
            new Member("9000000107", "Karan Mehta", "1991-12-25", Stage.KYC_SUBMITTED, null),
            new Member("9000000108", "Neha Joshi", "1994-06-17", Stage.REJECTED, null),
            new Member("9000000109", "Arjun Nair", null, Stage.REGISTERED, null),
            new Member("9000000110", "Ishita Das", null, Stage.REGISTERED, null));

    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;
    private final PaymentAccountRepository paymentAccountRepository;
    private final FileService fileService;
    private final KycService kycService;
    private final BankAccountService bankAccountService;
    private final AccountApprovalService accountApprovalService;
    private final InvestmentService investmentService;
    private final PnlService pnlService;
    private final WithdrawalService withdrawalService;
    private final SupportService supportService;
    private final Clock clock;

    public DemoDataSeeder(UserRepository userRepository, AdminUserRepository adminUserRepository,
                          PaymentAccountRepository paymentAccountRepository, FileService fileService,
                          KycService kycService, BankAccountService bankAccountService,
                          AccountApprovalService accountApprovalService, InvestmentService investmentService,
                          PnlService pnlService, WithdrawalService withdrawalService,
                          SupportService supportService, Clock clock) {
        this.userRepository = userRepository;
        this.adminUserRepository = adminUserRepository;
        this.paymentAccountRepository = paymentAccountRepository;
        this.fileService = fileService;
        this.kycService = kycService;
        this.bankAccountService = bankAccountService;
        this.accountApprovalService = accountApprovalService;
        this.investmentService = investmentService;
        this.pnlService = pnlService;
        this.withdrawalService = withdrawalService;
        this.supportService = supportService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByMobile(MARKER_MOBILE).isPresent()) {
            log.info("Demo data already present; skipping.");
            return;
        }
        AdminUser superAdmin = admin("super@alliancetrading.local");
        AdminUser admin = admin("admin@alliancetrading.local");
        UUID paymentAccountId = paymentAccountRepository.findByStatusOrderByLabelAsc(PaymentAccountStatus.ACTIVE)
                .getFirst().getId();
        log.info("Seeding demo members, investments, P&L, withdrawals and support tickets...");

        for (Member m : MEMBERS) {
            User user = userRepository.save(new User(m.mobile(), clock.instant()));
            if (m.stage() == Stage.REGISTERED) {
                continue;
            }
            kycService.submit(user.getId(), new KycSubmitRequest(m.name(), LocalDate.parse(m.dob()),
                    pan(m.mobile()), aadhaar(m.mobile())));
            bankAccountService.submit(user.getId(), new BankAccountRequest(m.name(),
                    "501000" + m.mobile().substring(2), "HDFC0001234", BankAccountType.SAVINGS,
                    m.name().split(" ")[0].toLowerCase() + "@okhdfc", null));

            switch (m.stage()) {
                case KYC_SUBMITTED -> { }
                case REJECTED -> kycService.reject(user.getId(), admin.getId(), "PAN name does not match bank account name");
                default -> {
                    accountApprovalService.approve(user.getId(), admin.getId(),
                            new PaymentService.AssignmentCommand(paymentAccountId, null, false, new BigDecimal(m.amount())));
                    Investment inv = investmentService.submit(user.getId(),
                            new InvestmentRequest(placeholderProof(user.getId(), m.name(), m.amount())), "demo-investment-" + m.mobile());
                    if (m.stage() == Stage.ACTIVE) {
                        investmentService.approve(inv.getId(), admin.getId());
                    }
                }
            }
        }

        // Three approved daily P&L batches (percentage of principal), posted by the super admin, approved by admin
        LocalDate today = LocalDate.now(clock.withZone(IST));
        String[][] days = {{"3", "1.2"}, {"2", "-0.4"}, {"1", "0.8"}};
        for (String[] d : days) {
            PnlBatch batch = pnlService.createBatch(superAdmin.getId(), new PnlService.CreateBatchCommand(
                    PnlBatchType.REGULAR, PnlValueType.PERCENTAGE, today.minus(Period.ofDays(Integer.parseInt(d[0]))),
                    null, null, null, "Demo session " + d[1] + "%", admin.getId(),
                    List.of(new PnlService.LineInput(PnlTargetType.ALL, null, null, new BigDecimal(d[1])))));
            pnlService.approve(batch.getId(), admin.getId());
        }
        // Today's batch waits for approval, so the checker queue is not empty
        pnlService.createBatch(superAdmin.getId(), new PnlService.CreateBatchCommand(
                PnlBatchType.REGULAR, PnlValueType.PERCENTAGE, today, null, null, null, "Demo session (awaiting approval)",
                admin.getId(), List.of(new PnlService.LineInput(PnlTargetType.ALL, null, null, new BigDecimal("0.5")))));

        // Withdrawals: one paid, one waiting for approval (both from ₹50,000 members, whose profit clears the minimum)
        UUID priya = memberId("9000000102");
        WithdrawalRequest paid = withdrawalService.request(priya,
                withdrawalService.quote(priya, new BigDecimal("600"), WithdrawalType.PROFIT).quote().getId(), "demo-wd-1");
        withdrawalService.approve(paid.getId(), admin.getId());
        withdrawalService.markPaid(paid.getId(), superAdmin.getId(), "DEMOUTR0000000001", null);

        UUID rohan = memberId("9000000103");
        withdrawalService.request(rohan,
                withdrawalService.quote(rohan, new BigDecimal("500"), WithdrawalType.PROFIT).quote().getId(), "demo-wd-2");

        // Support tickets
        supportService.open(memberId("9000000105"), "Payment done but still pending",
                "I paid ₹10,000 by UPI this morning and uploaded the screenshot. When will it be approved?");
        var ticket = supportService.open(rohan, "Withdrawal timeline",
                "How long does a profit withdrawal take to reach my bank account?");
        supportService.replyAsAdmin(admin.getId(), ticket.ticket().getId(),
                "Approved withdrawals are usually paid within one working day.");

        log.info("Demo data seeded: 10 members (9000000101-110), 4 active investments, 1 pending proof, "
                + "3 approved P&L days + 1 awaiting approval, 2 withdrawals, 2 support tickets.");
    }

    private AdminUser admin(String email) {
        return adminUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Seed admin " + email + " missing"));
    }

    private UUID memberId(String mobile) {
        return userRepository.findByMobile(mobile).orElseThrow().getId();
    }

    /** Unique, format-valid PAN per member, e.g. AXZPS0101K. */
    private static String pan(String mobile) {
        return "AXZPS" + mobile.substring(6) + "K";
    }

    /** Unique Aadhaar per member with a valid Verhoeff check digit. */
    private static String aadhaar(String mobile) {
        String base = "234567" + mobile.substring(5);
        for (int d = 0; d <= 9; d++) {
            if (AadhaarValidator.isValid(base + d)) {
                return base + d;
            }
        }
        throw new IllegalStateException("No valid Aadhaar check digit for " + base);
    }

    /** Uploads a generated "UPI receipt" PNG as the member's payment proof, exactly like an app upload. */
    private UUID placeholderProof(UUID userId, String name, String amount) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(480, 260,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setColor(new java.awt.Color(0x1F, 0x8A, 0x4C));
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 22));
            g.drawString("Payment successful (DEMO)", 24, 48);
            g.setColor(java.awt.Color.DARK_GRAY);
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 18));
            g.drawString("Rs. " + amount + " to alliancetrading@upi", 24, 100);
            g.drawString("From: " + name, 24, 140);
            g.drawString("Sample file seeded for local testing", 24, 200);
        } finally {
            g.dispose();
        }
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(img, "png", png);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not render demo payment proof", e);
        }
        String fileName = "upi-receipt-" + name.toLowerCase(java.util.Locale.ROOT).replace(' ', '-') + ".png";
        return fileService.upload(png.toByteArray(), fileName, DocType.PAYMENT_PROOF, userId, "USER", userId).getId();
    }
}
