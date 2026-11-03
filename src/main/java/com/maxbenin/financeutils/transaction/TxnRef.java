package com.maxbenin.financeutils.transaction;

import jakarta.persistence.*;

import java.time.Instant;

/** De onde um lançamento veio: origem, id na origem e o conteúdo original. */
@Entity
@Table(name = "txn_ref")
public class TxnRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "txn_id")
    private LedgerEntry entry;

    @Column(nullable = false)
    private String source;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "raw_data", nullable = false)
    private String rawData;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "changed_at")
    private Instant changedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    protected TxnRef() {
    }

    public TxnRef(LedgerEntry entry, String source, String externalId, String rawData, String hash, Instant now) {
        this.entry = entry;
        this.source = source;
        this.externalId = externalId;
        this.rawData = rawData;
        this.contentHash = hash;
        this.firstSeenAt = now;
        this.lastSeenAt = now;
    }

    /** Registra que a origem mostrou o registro de novo; devolve true se o conteúdo mudou. */
    public boolean seen(String rawData, String hash, Instant now) {
        lastSeenAt = now;
        removedAt = null;
        if (hash.equals(contentHash)) {
            return false;
        }
        this.rawData = rawData;
        this.contentHash = hash;
        this.changedAt = now;
        return true;
    }

    public void markRemoved(Instant now) {
        if (removedAt == null) {
            removedAt = now;
        }
    }

    public LedgerEntry getEntry() { return entry; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
    public String getRawData() { return rawData; }
    public String getContentHash() { return contentHash; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getChangedAt() { return changedAt; }
    public Instant getRemovedAt() { return removedAt; }
    public boolean isRemoved() { return removedAt != null; }
}
