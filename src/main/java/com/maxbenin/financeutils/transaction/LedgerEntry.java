package com.maxbenin.financeutils.transaction;

import com.maxbenin.financeutils.dinzo.DinzoTransaction;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/** Lançamento espelhado do Dinzo; a chave é o id do Dinzo, para rastrear mudanças. */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    @Id
    @Column(name = "dinzo_id")
    private String dinzoId;

    @Column(name = "account_id", nullable = false)
    private String accountId;
    @Column(name = "card_final")
    private String cardFinal;
    @Column(name = "entry_date", nullable = false)
    private LocalDate date;
    @Column(nullable = false)
    private String direction;
    @Column(nullable = false)
    private String status;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    private String description;
    @Column(name = "original_description")
    private String originalDescription;
    @Column(name = "category_id")
    private String categoryId;
    @Column(nullable = false)
    private String source;
    @Column(name = "installment_current")
    private Integer installmentCurrent;
    @Column(name = "installment_total")
    private Integer installmentTotal;
    @Column(name = "recurrence_id")
    private String recurrenceId;
    @Column(name = "transfer_group_id")
    private String transferGroupId;
    @Column(name = "bill_to_account_id")
    private String billToAccountId;
    @Column(name = "hidden_in_dinzo", nullable = false)
    private boolean hiddenInDinzo;
    @Column(name = "raw_json", nullable = false)
    private String rawJson;
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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ledger_entry_tag", joinColumns = @JoinColumn(name = "dinzo_id"))
    @Column(name = "tag")
    private Set<String> tags = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    private EntryKind kind;
    @Enumerated(EnumType.STRING)
    private Nature nature;
    @Column(name = "profile_id")
    private Long profileId;
    @Column(name = "subprofile_id")
    private Long subprofileId;
    @Column(name = "assignment_rule")
    private String assignmentRule;
    private String contributor;
    @Column(name = "merged_into_id")
    private String mergedIntoId;
    private Boolean superfluous;

    protected LedgerEntry() {
    }

    public static LedgerEntry create(DinzoTransaction t, String hash, Instant now) {
        LedgerEntry e = new LedgerEntry();
        e.dinzoId = t.id();
        e.firstSeenAt = now;
        e.copyFrom(t, hash);
        e.lastSeenAt = now;
        return e;
    }

    /** Atualiza a partir do Dinzo; devolve true se o conteúdo mudou. */
    public boolean refresh(DinzoTransaction t, String hash, Instant now) {
        lastSeenAt = now;
        removedAt = null;
        if (hash.equals(contentHash)) {
            return false;
        }
        copyFrom(t, hash);
        changedAt = now;
        return true;
    }

    private void copyFrom(DinzoTransaction t, String hash) {
        accountId = t.accountId();
        cardFinal = t.cardNumber();
        date = t.date();
        direction = t.type();
        status = t.status();
        amount = t.amount();
        description = t.description();
        originalDescription = t.originalDescription();
        categoryId = t.categoryId();
        source = t.source();
        installmentCurrent = t.installmentCurrent();
        installmentTotal = t.installmentTotal();
        recurrenceId = t.recurrenceId();
        transferGroupId = t.transferGroupId();
        billToAccountId = t.billToAccountId();
        hiddenInDinzo = t.hideFromResults();
        rawJson = t.rawJson();
        contentHash = hash;
        tags.clear();
        tags.addAll(t.tags());
    }

    public void markRemoved(Instant now) {
        if (removedAt == null) {
            removedAt = now;
        }
    }

    public void mergeInto(String realId) {
        this.mergedIntoId = realId;
    }

    public void applyAssignment(Long profileId, Long subprofileId, String rule) {
        this.profileId = profileId;
        this.subprofileId = subprofileId;
        this.assignmentRule = rule;
    }

    public void applyClassification(EntryKind kind, Nature nature, String contributor) {
        this.kind = kind;
        this.nature = nature;
        this.contributor = contributor;
    }

    public void setSuperfluous(Boolean superfluous) {
        this.superfluous = superfluous;
    }

    public boolean isManual() { return "APP_MANUAL".equals(source); }
    public boolean isOpenFinance() { return "OPEN_FINANCE".equals(source); }
    public boolean isMerged() { return mergedIntoId != null; }
    public boolean isRemoved() { return removedAt != null; }

    /** Valor com sinal para somas de gasto: débito soma, crédito (estorno) subtrai. */
    public BigDecimal signedExpense() {
        return "DEBIT".equals(direction) ? amount : amount.negate();
    }

    public String getDinzoId() { return dinzoId; }
    public String getAccountId() { return accountId; }
    public String getCardFinal() { return cardFinal; }
    public LocalDate getDate() { return date; }
    public String getDirection() { return direction; }
    public String getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public String getOriginalDescription() { return originalDescription; }
    public String getCategoryId() { return categoryId; }
    public String getSource() { return source; }
    public Integer getInstallmentCurrent() { return installmentCurrent; }
    public Integer getInstallmentTotal() { return installmentTotal; }
    public String getRecurrenceId() { return recurrenceId; }
    public String getTransferGroupId() { return transferGroupId; }
    public String getBillToAccountId() { return billToAccountId; }
    public boolean isHiddenInDinzo() { return hiddenInDinzo; }
    public String getContentHash() { return contentHash; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getChangedAt() { return changedAt; }
    public Instant getRemovedAt() { return removedAt; }
    public Set<String> getTags() { return tags; }
    public EntryKind getKind() { return kind; }
    public Nature getNature() { return nature; }
    public Long getProfileId() { return profileId; }
    public Long getSubprofileId() { return subprofileId; }
    public String getAssignmentRule() { return assignmentRule; }
    public String getContributor() { return contributor; }
    public String getMergedIntoId() { return mergedIntoId; }
    public Boolean getSuperfluous() { return superfluous; }
}
