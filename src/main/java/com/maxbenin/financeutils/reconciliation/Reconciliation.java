package com.maxbenin.financeutils.reconciliation;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Vínculo entre um lançamento manual e o lançamento real do Open Finance. */
@Entity
@Table(name = "txn_reconciliation")
public class Reconciliation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manual_id", nullable = false)
    private String manualId;

    @Column(name = "real_id", nullable = false)
    private String realId;

    @Column(nullable = false, precision = 5, scale = 3)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReconciliationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected Reconciliation() {
    }

    public Reconciliation(String manualId, String realId, double score, ReconciliationStatus status, Instant now) {
        this.manualId = manualId;
        this.realId = realId;
        this.score = BigDecimal.valueOf(score);
        this.status = status;
        this.createdAt = now;
        if (status == ReconciliationStatus.AUTO) {
            this.decidedAt = now;
        }
    }

    public void decide(boolean accepted, Instant now) {
        this.status = accepted ? ReconciliationStatus.CONFIRMED : ReconciliationStatus.REJECTED;
        this.decidedAt = now;
    }

    public Long getId() { return id; }
    public String getManualId() { return manualId; }
    public String getRealId() { return realId; }
    public BigDecimal getScore() { return score; }
    public ReconciliationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
