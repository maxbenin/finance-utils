package com.maxbenin.financeutils.transaction;

import com.maxbenin.financeutils.dinzo.DinzoAccount;
import jakarta.persistence.*;

import java.time.Instant;

/** Espelho de uma conta do Dinzo; {@code tracked=false} deixa a conta fora dos cálculos. */
@Entity
@Table(name = "dinzo_account")
public class TrackedAccount {

    @Id
    @Column(name = "dinzo_id")
    private String dinzoId;

    @Column(nullable = false)
    private String title;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(name = "sub_type")
    private String subType;

    private boolean tracked = true;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    protected TrackedAccount() {
    }

    public TrackedAccount(String dinzoId, String title, String accountType, boolean tracked) {
        this.dinzoId = dinzoId;
        this.title = title;
        this.accountType = accountType;
        this.tracked = tracked;
    }

    public void refresh(DinzoAccount a, Instant now) {
        title = a.title();
        accountType = a.type();
        subType = a.subType();
        lastSyncedAt = now;
    }

    public boolean isCreditCard() {
        return "CREDIT".equals(accountType);
    }

    public String getDinzoId() { return dinzoId; }
    public String getTitle() { return title; }
    public String getAccountType() { return accountType; }
    public String getSubType() { return subType; }
    public boolean isTracked() { return tracked; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
}
