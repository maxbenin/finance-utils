package com.maxbenin.financeutils.account;

import jakarta.persistence.*;

/** Identificador da conta numa origem externa (Dinzo, banco do OFX...). */
@Entity
@Table(name = "account_ref")
public class AccountRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String source;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    protected AccountRef() {
    }

    public AccountRef(String accountId, String source, String externalId) {
        this.accountId = accountId;
        this.source = source;
        this.externalId = externalId;
    }

    public String getAccountId() { return accountId; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
}
