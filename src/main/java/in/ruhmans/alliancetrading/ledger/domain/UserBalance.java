package in.ruhmans.alliancetrading.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Cached per-user balances. Always written via LedgerPostingService under a
 * pessimistic lock.
 */
@Entity
@Table(name = "user_balances")
public class UserBalance {

    @Id
    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal principal = BigDecimal.ZERO.setScale(2);

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal profit = BigDecimal.ZERO.setScale(2);

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal held = BigDecimal.ZERO.setScale(2);

    @Version
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserBalance() {}

    public UserBalance(UUID userId, Instant now) {
        this.userId = userId;
        this.updatedAt = now;
    }

    public UUID getUserId() { return userId; }
    public BigDecimal getPrincipal() { return principal; }
    public BigDecimal getProfit() { return profit; }
    public BigDecimal getHeld() { return held; }
    public Long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void apply(Bucket bucket, BigDecimal delta, Instant now) {
        switch (bucket) {
            case PRINCIPAL -> principal = principal.add(delta);
            case PROFIT -> profit = profit.add(delta);
            case HELD -> held = held.add(delta);
        }
        this.updatedAt = now;
    }

    public BigDecimal currentValue() {
        return principal.add(profit);
    }

    public BigDecimal withdrawableProfit() {
        return profit.signum() > 0 ? profit : BigDecimal.ZERO.setScale(2);
    }
}
