package in.ruhmans.alliancetrading.admin.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Overview page and sidebar counts. Everything comes from two SQL statements (queue counts, then
 * investment and P&L aggregates), so the page costs two database round trips. Day and month
 * boundaries are IST.
 */
@Service
public class AdminDashboardService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @PersistenceContext
    private EntityManager entityManager;

    private final Clock clock;

    public AdminDashboardService(Clock clock) {
        this.clock = clock;
    }

    public record ActiveInvestors(long value, long newThisMonth) {}
    public record FundsInvested(BigDecimal value, BigDecimal thisMonth) {}
    public record ProfitCredited(BigDecimal value, BigDecimal today) {}
    public record PendingActions(long value) {}
    public record Stats(ActiveInvestors activeInvestors, FundsInvested fundsInvested,
                        ProfitCredited profitCredited, PendingActions pendingActions) {}

    public record ActionCenter(long kycPending, long investmentPending, long withdrawalOpen, long supportOpen,
                               long bankPending, long pnlAwaitingMyApproval, long total) {}

    public record TodayDistribution(LocalDate sessionDate, BigDecimal total, long recipients, long eligible) {}

    public record DashboardSummary(Stats stats, ActionCenter actionCenter, TodayDistribution todayDistribution) {}

    private static final String QUEUE_SQL = """
            SELECT
              (SELECT count(*) FROM users WHERE status = 'KYC_SUBMITTED'),
              (SELECT count(*) FROM investments WHERE status = 'PENDING_VERIFICATION'),
              (SELECT count(*) FROM withdrawal_requests WHERE status IN ('REQUESTED','APPROVED','PAYOUT_FAILED')),
              (SELECT count(*) FROM support_tickets WHERE status = 'OPEN'),
              (SELECT count(*) FROM bank_accounts WHERE status = 'PENDING_VERIFICATION'),
              (SELECT count(*) FROM pnl_batches WHERE status = 'DRAFT' AND assigned_checker_id = :adminId)
            """;

    private static final String TOTALS_SQL = """
            SELECT i.active_investors, i.new_this_month, i.funds, i.funds_this_month, i.eligible,
                   p.profit_total, p.profit_today, p.recipients_today
            FROM (SELECT count(DISTINCT user_id) FILTER (WHERE status = 'ACTIVE')                          AS active_investors,
                         count(DISTINCT user_id) FILTER (WHERE status = 'ACTIVE' AND approved_at >= :monthStart) AS new_this_month,
                         COALESCE(sum(remaining_principal) FILTER (WHERE status = 'ACTIVE'), 0)            AS funds,
                         COALESCE(sum(amount) FILTER (WHERE status = 'ACTIVE' AND approved_at >= :monthStart), 0) AS funds_this_month,
                         count(*) FILTER (WHERE status = 'ACTIVE')                                         AS eligible
                  FROM investments) i,
                 (SELECT COALESCE(sum(amount), 0)                                     AS profit_total,
                         COALESCE(sum(amount) FILTER (WHERE session_date = :today), 0) AS profit_today,
                         count(DISTINCT investment_id) FILTER (WHERE session_date = :today) AS recipients_today
                  FROM pnl_allocations) p
            """;

    @Transactional(readOnly = true)
    public DashboardSummary summary(UUID adminId) {
        LocalDate today = LocalDate.now(clock.withZone(IST));
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(IST).toInstant();

        Object[] q = (Object[]) entityManager.createNativeQuery(QUEUE_SQL)
                .setParameter("adminId", adminId)
                .getSingleResult();
        long kyc = num(q[0]);
        long investment = num(q[1]);
        long withdrawal = num(q[2]);
        long support = num(q[3]);
        long bank = num(q[4]);
        long pnlMine = num(q[5]);
        long pending = kyc + investment + withdrawal + support + bank + pnlMine;

        Object[] t = (Object[]) entityManager.createNativeQuery(TOTALS_SQL)
                .setParameter("monthStart", Timestamp.from(monthStart))
                .setParameter("today", today)
                .getSingleResult();
        BigDecimal profitToday = money(t[6]);

        Stats stats = new Stats(
                new ActiveInvestors(num(t[0]), num(t[1])),
                new FundsInvested(money(t[2]), money(t[3])),
                new ProfitCredited(money(t[5]), profitToday),
                new PendingActions(pending));
        ActionCenter actions = new ActionCenter(kyc, investment, withdrawal, support, bank, pnlMine, pending);
        TodayDistribution distribution = new TodayDistribution(today, profitToday, num(t[7]), num(t[4]));
        return new DashboardSummary(stats, actions, distribution);
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static BigDecimal money(Object o) {
        BigDecimal v = o == null ? BigDecimal.ZERO
                : o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
