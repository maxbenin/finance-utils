package com.maxbenin.financeutils.transaction;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Lançamento do app. As origens externas ficam em {@link TxnRef}. */
@Entity
@Table(name = "txn")
public class LedgerEntry {

    public static final String ORIGIN_APP = "APP";

    @Id
    private String id;

    @Column(name = "account_id", nullable = false)
    private String accountId;
    @Column(name = "txn_date", nullable = false)
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
    private String notes;
    @Column(name = "category_id")
    private String categoryId;
    @Column(name = "card_final")
    private String cardFinal;
    @Column(name = "installment_current")
    private Integer installmentCurrent;
    @Column(name = "installment_total")
    private Integer installmentTotal;
    @Column(name = "recurrence_ref")
    private String recurrenceRef;
    private boolean manual;
    @Column(nullable = false)
    private String origin;
    @Column(name = "bill_payment")
    private boolean billPayment;
    @Column(name = "transfer_group_ref")
    private String transferGroupRef;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "removed_at")
    private Instant removedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "txn_tag", joinColumns = @JoinColumn(name = "txn_id"))
    @Column(name = "tag")
    private Set<String> tags = new LinkedHashSet<>();

    // Calculado pelo processamento
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
    @Column(name = "counterpart_profile_id")
    private Long counterpartProfileId;
    @Column(name = "transfer_pair_id")
    private String transferPairId;
    @Column(name = "auto_exclude_reason")
    private String autoExcludeReason;

    // Decisões do usuário
    @Enumerated(EnumType.STRING)
    @Column(name = "totals_override")
    private TotalsOverride totalsOverride;
    @Column(name = "exclude_reason")
    private String excludeReason;
    @Column(name = "profile_override_id")
    private Long profileOverrideId;
    private Boolean superfluous;
    @Column(name = "merged_into_id")
    private String mergedIntoId;

    protected LedgerEntry() {
    }

    /** Lançamento novo vindo de uma origem externa; registros do Dinzo reaproveitam o id do Dinzo. */
    public static LedgerEntry fromImport(String id, ImportedTxn t, Instant now) {
        LedgerEntry e = new LedgerEntry();
        e.id = id == null ? UUID.randomUUID().toString() : id;
        e.origin = t.source();
        e.createdAt = now;
        e.categoryId = t.categoryId();
        e.notes = t.notes();
        e.applyImport(t, now);
        return e;
    }

    /** Lançamento manual feito no app. */
    public static LedgerEntry manual(String accountId, LocalDate date, String direction, BigDecimal amount,
                                     String description, String categoryId, String cardFinal,
                                     Collection<String> tags, String notes, Instant now) {
        LedgerEntry e = new LedgerEntry();
        e.id = UUID.randomUUID().toString();
        e.origin = ORIGIN_APP;
        e.manual = true;
        e.accountId = accountId;
        e.date = date;
        e.direction = direction;
        e.status = "POSTED";
        e.amount = amount;
        e.description = description;
        e.categoryId = categoryId;
        e.cardFinal = cardFinal;
        e.notes = notes;
        e.tags.addAll(tags);
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    /** Atualiza os dados que vêm do banco. A categoria só é preenchida se ainda não houver uma (o usuário refina no app). */
    public void applyImport(ImportedTxn t, Instant now) {
        accountId = t.accountId();
        date = t.date();
        direction = t.direction();
        status = t.status();
        amount = t.amount();
        description = t.description();
        originalDescription = t.originalDescription();
        cardFinal = t.cardFinal();
        installmentCurrent = t.installmentCurrent();
        installmentTotal = t.installmentTotal();
        recurrenceRef = t.recurrenceRef();
        manual = t.manual();
        billPayment = t.billPayment();
        transferGroupRef = t.transferGroupRef();
        if (categoryId == null) {
            categoryId = t.categoryId();
        }
        tags.clear();
        tags.addAll(t.tags());
        removedAt = null;
        updatedAt = now;
    }

    public void markRemoved(Instant now) {
        if (removedAt == null) {
            removedAt = now;
            updatedAt = now;
        }
    }

    public void mergeInto(String realId) { this.mergedIntoId = realId; }

    public void applyAssignment(Long profileId, Long subprofileId, String rule) {
        this.profileId = profileId;
        this.subprofileId = subprofileId;
        this.assignmentRule = rule;
    }

    public void applyTransfer(String pairId, Long counterpartProfileId, String autoExcludeReason) {
        this.transferPairId = pairId;
        this.counterpartProfileId = counterpartProfileId;
        this.autoExcludeReason = autoExcludeReason;
    }

    public void applyClassification(EntryKind kind, Nature nature, String contributor) {
        this.kind = kind;
        this.nature = nature;
        this.contributor = contributor;
    }

    public void setTotalsOverride(TotalsOverride override, String reason, Instant now) {
        this.totalsOverride = override;
        this.excludeReason = override == TotalsOverride.EXCLUDE ? reason : null;
        this.updatedAt = now;
    }

    public void setProfileOverrideId(Long profileOverrideId, Instant now) {
        this.profileOverrideId = profileOverrideId;
        this.updatedAt = now;
    }

    public void setSuperfluous(Boolean superfluous, Instant now) {
        this.superfluous = superfluous;
        this.updatedAt = now;
    }

    public void setCategoryId(String categoryId, Instant now) {
        this.categoryId = categoryId;
        this.updatedAt = now;
    }

    /** Fora dos totais por decisão do usuário ou por sugestão automática que ele não desfez. */
    public boolean isExcludedFromTotals() {
        return totalsOverride == TotalsOverride.EXCLUDE
                || (totalsOverride == null && autoExcludeReason != null);
    }

    /** Motivo exibido para estar fora dos totais. */
    public String getEffectiveExcludeReason() {
        if (totalsOverride == TotalsOverride.EXCLUDE) {
            return excludeReason == null || excludeReason.isBlank() ? "Marcado pelo usuário" : excludeReason;
        }
        return totalsOverride == null ? autoExcludeReason : null;
    }

    public boolean isMerged() { return mergedIntoId != null; }
    public boolean isRemoved() { return removedAt != null; }
    public boolean isDebit() { return "DEBIT".equals(direction); }

    /** Valor com sinal para somas de gasto: débito soma, crédito (estorno no cartão) subtrai. */
    public BigDecimal signedExpense() {
        return isDebit() ? amount : amount.negate();
    }

    public String getId() { return id; }
    public String getAccountId() { return accountId; }
    public LocalDate getDate() { return date; }
    public String getDirection() { return direction; }
    public String getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public String getOriginalDescription() { return originalDescription; }
    public String getNotes() { return notes; }
    public String getCategoryId() { return categoryId; }
    public String getCardFinal() { return cardFinal; }
    public Integer getInstallmentCurrent() { return installmentCurrent; }
    public Integer getInstallmentTotal() { return installmentTotal; }
    public String getRecurrenceRef() { return recurrenceRef; }
    public boolean isManual() { return manual; }
    public String getOrigin() { return origin; }
    public boolean isBillPayment() { return billPayment; }
    public String getTransferGroupRef() { return transferGroupRef; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getRemovedAt() { return removedAt; }
    public Set<String> getTags() { return tags; }
    public EntryKind getKind() { return kind; }
    public Nature getNature() { return nature; }
    public Long getProfileId() { return profileId; }
    public Long getSubprofileId() { return subprofileId; }
    public String getAssignmentRule() { return assignmentRule; }
    public String getContributor() { return contributor; }
    public Long getCounterpartProfileId() { return counterpartProfileId; }
    public String getTransferPairId() { return transferPairId; }
    public String getAutoExcludeReason() { return autoExcludeReason; }
    public TotalsOverride getTotalsOverride() { return totalsOverride; }
    public String getExcludeReason() { return excludeReason; }
    public Long getProfileOverrideId() { return profileOverrideId; }
    public Boolean getSuperfluous() { return superfluous; }
    public String getMergedIntoId() { return mergedIntoId; }
}
