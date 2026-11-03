package com.maxbenin.financeutils.sync;

import com.maxbenin.financeutils.account.*;
import com.maxbenin.financeutils.category.Category;
import com.maxbenin.financeutils.category.CategoryRef;
import com.maxbenin.financeutils.category.CategoryRefRepository;
import com.maxbenin.financeutils.category.CategoryRepository;
import com.maxbenin.financeutils.dinzo.DinzoAccount;
import com.maxbenin.financeutils.dinzo.DinzoCategory;
import com.maxbenin.financeutils.dinzo.DinzoClient;
import com.maxbenin.financeutils.dinzo.DinzoTransaction;
import com.maxbenin.financeutils.reconciliation.ManualMatcher;
import com.maxbenin.financeutils.reconciliation.Reconciliation;
import com.maxbenin.financeutils.reconciliation.ReconciliationRepository;
import com.maxbenin.financeutils.reconciliation.ReconciliationStatus;
import com.maxbenin.financeutils.transaction.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Traz dados do Dinzo para o app. O Dinzo é só uma origem: cada registro vira (ou atualiza)
 * um lançamento do app e fica guardado como referência externa.
 */
@Service
public class SyncService {

    public static final String SOURCE = "DINZO";

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final List<ReconciliationStatus> SETTLED =
            List.of(ReconciliationStatus.AUTO, ReconciliationStatus.CONFIRMED);

    private final DinzoClient dinzo;
    private final LedgerEntryRepository entries;
    private final TxnRefRepository txnRefs;
    private final AccountRepository accounts;
    private final AccountRefRepository accountRefs;
    private final CategoryRepository categories;
    private final CategoryRefRepository categoryRefs;
    private final ReconciliationRepository reconciliations;
    private final EntryProcessor processor;
    private final SyncProperties props;
    private final Clock clock;
    private final ManualMatcher matcher = ManualMatcher.defaults();

    public SyncService(DinzoClient dinzo, LedgerEntryRepository entries, TxnRefRepository txnRefs,
                       AccountRepository accounts, AccountRefRepository accountRefs,
                       CategoryRepository categories, CategoryRefRepository categoryRefs,
                       ReconciliationRepository reconciliations, EntryProcessor processor,
                       SyncProperties props, Clock clock) {
        this.dinzo = dinzo;
        this.entries = entries;
        this.txnRefs = txnRefs;
        this.accounts = accounts;
        this.accountRefs = accountRefs;
        this.categories = categories;
        this.categoryRefs = categoryRefs;
        this.reconciliations = reconciliations;
        this.processor = processor;
        this.props = props;
        this.clock = clock;
    }

    /** Sincronização periódica: os últimos {@code lookbackDays} dias. */
    @Transactional
    public synchronized SyncResult sync() {
        LocalDate today = LocalDate.now(clock);
        return syncPeriod(today.minusDays(props.lookbackDays()), today);
    }

    /** Carga inicial: os últimos {@code initialLoadMonths} meses, a partir do dia 1. */
    @Transactional
    public synchronized SyncResult initialLoad() {
        LocalDate today = LocalDate.now(clock);
        return syncPeriod(today.minusMonths(props.initialLoadMonths()).withDayOfMonth(1), today);
    }

    private SyncResult syncPeriod(LocalDate from, LocalDate to) {
        Instant now = clock.instant();

        int newCategories = syncCategories(now);
        int newAccounts = syncAccounts(now);
        Map<String, String> accountIds = refMap(accountRefs.findAll().stream()
                .filter(r -> SOURCE.equals(r.getSource())).toList(), AccountRef::getExternalId, AccountRef::getAccountId);
        Map<String, String> categoryIds = refMap(categoryRefs.findBySource(SOURCE),
                CategoryRef::getExternalId, CategoryRef::getCategoryId);

        List<DinzoTransaction> fetched = dinzo.listTransactions(from, to);
        Map<String, TxnRef> known = txnRefs.findBySourceInPeriod(SOURCE, from, to).stream()
                .collect(Collectors.toMap(TxnRef::getExternalId, Function.identity()));

        int created = 0, updated = 0, removed = 0;
        Set<String> seen = new HashSet<>();
        List<LedgerEntry> newReals = new ArrayList<>();
        for (DinzoTransaction t : fetched) {
            String accountId = accountIds.get(t.accountId());
            if (accountId == null) {
                log.warn("Lançamento {} de conta desconhecida {}: ignorado", t.id(), t.accountId());
                continue;
            }
            seen.add(t.id());
            ImportedTxn imported = toImported(t, accountId, categoryIds.get(t.categoryId()));
            String hash = sha256(t.rawJson());
            TxnRef ref = known.get(t.id());
            if (ref == null) {
                ref = txnRefs.findBySourceAndExternalId(SOURCE, t.id()).orElse(null); // mudou de data
            }
            if (ref == null) {
                LedgerEntry e = entries.save(LedgerEntry.fromImport(t.id(), imported, now));
                txnRefs.save(new TxnRef(e, SOURCE, t.id(), t.rawJson(), hash, now));
                created++;
                if (!e.isManual()) {
                    newReals.add(e);
                }
            } else if (ref.seen(t.rawJson(), hash, now) || ref.getEntry().isRemoved()) {
                ref.getEntry().applyImport(imported, now);
                updated++;
            }
        }
        for (TxnRef ref : known.values()) {
            if (!seen.contains(ref.getExternalId()) && !ref.isRemoved()) {
                ref.markRemoved(now);
                if (SOURCE.equals(ref.getEntry().getOrigin())) {
                    ref.getEntry().markRemoved(now);
                }
                removed++;
            }
        }
        entries.flush();

        int[] merge = reconcile(newReals, from, to, now);
        int unassigned = processor.process(from, to);

        SyncResult result = new SyncResult(now, from, to, fetched.size(), created, updated, removed,
                merge[0], merge[1], unassigned, newAccounts, newCategories);
        log.info("Sincronização com o Dinzo concluída: {}", result);
        return result;
    }

    /** Cria no app as categorias do Dinzo que ainda não existem. As existentes são do app e não mudam. */
    private int syncCategories(Instant now) {
        List<DinzoCategory> remote = dinzo.listCategories();
        Map<String, String> ids = refMap(categoryRefs.findBySource(SOURCE), CategoryRef::getExternalId, CategoryRef::getCategoryId);
        List<DinzoCategory> created = new ArrayList<>();
        for (DinzoCategory c : remote) {
            if (!ids.containsKey(c.id())) {
                Category cat = categories.save(new Category(c.id(), c.title(), c.colorHex(), c.archived(), now));
                categoryRefs.save(new CategoryRef(cat.getId(), SOURCE, c.id()));
                ids.put(c.id(), cat.getId());
                created.add(c);
            }
        }
        for (DinzoCategory c : created) {
            if (c.parentId() != null && ids.containsKey(c.parentId())) {
                categories.findById(ids.get(c.id())).ifPresent(cat -> cat.setParentId(ids.get(c.parentId())));
            }
        }
        return created.size();
    }

    /** Cadastra contas novas do Dinzo e atualiza qual conta paga cada cartão. */
    private int syncAccounts(Instant now) {
        int created = 0;
        List<DinzoAccount> remote = dinzo.listAccounts();
        Map<String, Account> byExternal = new HashMap<>();
        for (DinzoAccount a : remote) {
            AccountKind kind = "CREDIT".equals(a.type()) ? AccountKind.CREDIT_CARD : AccountKind.CHECKING;
            Optional<AccountRef> ref = accountRefs.findBySourceAndExternalId(SOURCE, a.id());
            Account acc;
            if (ref.isPresent()) {
                acc = accounts.findById(ref.get().getAccountId()).orElseThrow();
            } else {
                // Pode existir sem referência: conta declarada no seed antes da primeira sincronização
                acc = accounts.findById(a.id()).orElse(null);
                if (acc == null) {
                    acc = accounts.save(new Account(a.id(), a.title(), kind, true, now));
                    created++;
                } else {
                    acc.rename(a.title());
                    acc.setKind(kind);
                }
                accountRefs.save(new AccountRef(acc.getId(), SOURCE, a.id()));
            }
            byExternal.put(a.id(), acc);
        }
        for (DinzoAccount a : remote) {
            Account payer = a.payBillFromAccountId() == null ? null : byExternal.get(a.payBillFromAccountId());
            byExternal.get(a.id()).setBillPaidFromAccountId(payer == null ? null : payer.getId());
        }
        return created;
    }

    /** @return [consolidados automaticamente, aguardando confirmação] */
    private int[] reconcile(List<LedgerEntry> newReals, LocalDate from, LocalDate to, Instant now) {
        if (newReals.isEmpty()) {
            return new int[]{0, 0};
        }
        List<LedgerEntry> pool = new ArrayList<>(entries.findOpenManuals(from.minusDays(5), to));
        int auto = 0, pending = 0;
        newReals.sort(Comparator.comparing(LedgerEntry::getDate));
        for (LedgerEntry real : newReals) {
            if (pool.isEmpty() || reconciliations.existsByRealIdAndStatusIn(real.getId(), SETTLED)) {
                continue;
            }
            List<ManualMatcher.Entry> candidates = pool.stream().map(SyncService::toMatch).toList();
            Optional<ManualMatcher.Match> match = matcher.findMatch(toMatch(real), candidates);
            if (match.isEmpty() || reconciliations.existsByManualIdAndRealId(match.get().manualId(), real.getId())) {
                continue;
            }
            ManualMatcher.Match m = match.get();
            LedgerEntry manual = pool.stream().filter(p -> p.getId().equals(m.manualId())).findFirst().orElseThrow();
            if (m.automatic()) {
                manual.mergeInto(real.getId());
                pool.remove(manual);
                reconciliations.save(new Reconciliation(manual.getId(), real.getId(), m.score(), ReconciliationStatus.AUTO, now));
                auto++;
            } else {
                reconciliations.save(new Reconciliation(manual.getId(), real.getId(), m.score(), ReconciliationStatus.PENDING_REVIEW, now));
                pending++;
            }
        }
        return new int[]{auto, pending};
    }

    static ImportedTxn toImported(DinzoTransaction t, String accountId, String categoryId) {
        return new ImportedTxn(SOURCE, t.id(), accountId, t.date(), t.type(), t.status(), t.amount(),
                t.description(), t.originalDescription(), t.notes(), categoryId, t.cardNumber(),
                t.installmentCurrent(), t.installmentTotal(), t.recurrenceId(), t.isManual(),
                t.billToAccountId() != null, t.transferGroupId(), t.tags(), t.rawJson());
    }

    private static ManualMatcher.Entry toMatch(LedgerEntry e) {
        return new ManualMatcher.Entry(e.getId(), e.getAccountId(), e.getDirection(), e.getAmount(),
                e.getDate(), e.getDescription());
    }

    private static <T> Map<String, String> refMap(List<T> refs, Function<T, String> key, Function<T, String> value) {
        Map<String, String> m = new HashMap<>();
        refs.forEach(r -> m.put(key.apply(r), value.apply(r)));
        return m;
    }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
