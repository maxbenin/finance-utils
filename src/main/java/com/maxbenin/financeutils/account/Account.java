package com.maxbenin.financeutils.account;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Conta corrente ou cartão de crédito do app; {@code tracked=false} deixa a conta fora dos cálculos. */
@Entity
@Table(name = "account")
public class Account {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountKind kind;

    private String institution;

    private boolean tracked = true;

    /** Para cartões: a conta corrente que paga a fatura. */
    @Column(name = "bill_paid_from_account_id")
    private String billPaidFromAccountId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(String id, String name, AccountKind kind, boolean tracked, Instant now) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.name = name;
        this.kind = kind;
        this.tracked = tracked;
        this.createdAt = now;
    }

    public void rename(String name) { this.name = name; }
    public void setKind(AccountKind kind) { this.kind = kind; }
    public void setInstitution(String institution) { this.institution = institution; }
    public void setBillPaidFromAccountId(String id) { this.billPaidFromAccountId = id; }

    public boolean isCreditCard() { return kind == AccountKind.CREDIT_CARD; }

    public String getId() { return id; }
    public String getName() { return name; }
    public AccountKind getKind() { return kind; }
    public String getInstitution() { return institution; }
    public boolean isTracked() { return tracked; }
    public String getBillPaidFromAccountId() { return billPaidFromAccountId; }
    public Instant getCreatedAt() { return createdAt; }
}
