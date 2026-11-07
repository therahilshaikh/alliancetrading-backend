package in.ruhmans.alliancetrading.withdrawal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.settings.domain.WithdrawalType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "withdrawal_requests")
public class WithdrawalRequest {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "investment_id", nullable = false, updatable = false)
    private UUID investmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_type", nullable = false, length = 10, updatable = false)
    private WithdrawalType withdrawalType;

    @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal grossAmount;

    @Column(name = "fee_setting_id", nullable = false, updatable = false)
    private UUID feeSettingId;

    @Column(name = "fee_pct", nullable = false, precision = 6, scale = 4, updatable = false)
    private BigDecimal feePct;

    @Column(name = "fee_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal feeAmount;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal netAmount;

    @Column(name = "bank_account_id", nullable = false, updatable = false)
    private UUID bankAccountId;

    @Column(name = "bank_holder_name", nullable = false, length = 200, updatable = false)
    private String bankHolderName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "bank_account_last4", nullable = false, length = 4, updatable = false)
    private String bankAccountLast4;

    @Column(name = "bank_ifsc", nullable = false, length = 11, updatable = false)
    private String bankIfsc;

    @Column(name = "bank_vpa", length = 100, updatable = false)
    private String bankVpa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WithdrawalStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "second_approved_by")
    private UUID secondApprovedBy;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "payout_utr", length = 22)
    private String payoutUtr;

    @Column(name = "payout_proof_document_id")
    private UUID payoutProofDocumentId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected WithdrawalRequest() {}

    public WithdrawalRequest(UUID userId, UUID investmentId, WithdrawalType withdrawalType,
                             BigDecimal grossAmount, UUID feeSettingId, BigDecimal feePct,
                             BigDecimal feeAmount, BigDecimal netAmount, UUID bankAccountId,
                             String bankHolderName, String bankAccountLast4, String bankIfsc,
                             String bankVpa, String idempotencyKey, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.investmentId = investmentId;
        this.withdrawalType = withdrawalType;
        this.grossAmount = grossAmount;
        this.feeSettingId = feeSettingId;
        this.feePct = feePct;
        this.feeAmount = feeAmount;
        this.netAmount = netAmount;
        this.bankAccountId = bankAccountId;
        this.bankHolderName = bankHolderName;
        this.bankAccountLast4 = bankAccountLast4;
        this.bankIfsc = bankIfsc;
        this.bankVpa = bankVpa;
        this.idempotencyKey = idempotencyKey;
        this.status = WithdrawalStatus.REQUESTED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getInvestmentId() { return investmentId; }
    public WithdrawalType getWithdrawalType() { return withdrawalType; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public UUID getFeeSettingId() { return feeSettingId; }
    public BigDecimal getFeePct() { return feePct; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public BigDecimal getNetAmount() { return netAmount; }
    public UUID getBankAccountId() { return bankAccountId; }
    public String getBankHolderName() { return bankHolderName; }
    public String getBankAccountLast4() { return bankAccountLast4; }
    public String getBankIfsc() { return bankIfsc; }
    public String getBankVpa() { return bankVpa; }
    public WithdrawalStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getApprovedBy() { return approvedBy; }
    public UUID getSecondApprovedBy() { return secondApprovedBy; }
    public UUID getPaidBy() { return paidBy; }
    public String getPayoutUtr() { return payoutUtr; }
    public UUID getPayoutProofDocumentId() { return payoutProofDocumentId; }
    public Instant getPaidAt() { return paidAt; }
    public String getRejectionReason() { return rejectionReason; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void approve(UUID adminId, Instant now) {
        this.status = WithdrawalStatus.APPROVED;
        this.approvedBy = adminId;
        this.updatedAt = now;
    }

    public void secondApprove(UUID adminId, Instant now) {
        this.secondApprovedBy = adminId;
        this.updatedAt = now;
    }

    public void markPaid(UUID adminId, String payoutUtr, UUID payoutProofDocumentId, Instant now) {
        this.status = WithdrawalStatus.PAID;
        this.paidBy = adminId;
        this.payoutUtr = payoutUtr;
        this.payoutProofDocumentId = payoutProofDocumentId;
        this.paidAt = now;
        this.updatedAt = now;
    }

    public void markFailed(String reason, Instant now) {
        this.status = WithdrawalStatus.PAYOUT_FAILED;
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.status = WithdrawalStatus.CANCELLED;
        this.updatedAt = now;
    }

    public void reject(String reason, Instant now) {
        this.status = WithdrawalStatus.REJECTED;
        this.rejectionReason = reason;
        this.updatedAt = now;
    }
}
