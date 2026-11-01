package com.maxbenin.financeutils.sync;

import com.maxbenin.financeutils.dinzo.DinzoAccount;
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

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final List<ReconciliationStatus> SETTLED =
            List.of(ReconciliationStatus.AUTO, ReconciliationStatus.CONFIRMED);

    private final DinzoClient dinzo;
    private final LedgerEntryRepository entries;
    private final TrackedAccountRepository accounts;
    private final ReconciliationRepository reconciliations;
    private final EntryProcessor processor;
    private final SyncProperties props;
    private final Clock clock;
    private final ManualMatcher matcher = ManualMatcher.defaults();

    public SyncService(DinzoClient dinzo, LedgerEntryRepository entries, TrackedAccountRepository accounts,
                       ReconciliationRepository reconciliations, EntryProcessor processor,
                       SyncProperties props, Clock clock) {
        this.dinzo = dinzo;
        this.entries = entries;
        this.accounts = accounts;
        this.reconciliations = reconciliations;
        this.processor = processor;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public synchronized SyncResult sync() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(props.lookbackDays());

        syncAccounts(now);

        List<DinzoTransaction> fetched = dinzo.listTransactions(from, today);
        Map<String, LedgerEntry> existing = entries.findByDateBetween(from, today).stream()
                .collect(Collectors.toMap(LedgerEntry::getDinzoId, Function.identity()));

        int created = 0, updated = 0, removed = 0;
        Set<String> seen = new HashSet<>();
        List<LedgerEntry> newReals = new ArrayList<>();
        for (DinzoTransaction t : fetched) {
            seen.add(t.id());
            String hash = sha256(t.rawJson());
            LedgerEntry e = existing.get(t.id());
            if (e == null) {
                e = entries.findById(t.id()).orElse(null); // pode ter mudado de data
            }
            if (e == null) {
                e = entries.save(LedgerEntry.create(t, hash, now));
                created++;
                if (e.isOpenFinance()) {
                    newReals.add(e);
                }
            } else if (e.refresh(t, hash, now)) {
                updated++;
            }
        }
        for (LedgerEntry e : existing.values()) {
            if (!seen.contains(e.getDinzoId()) && !e.isRemoved()) {
                e.markRemoved(now);
                removed++;
            }
        }
        entries.flush();

        int[] merge = reconcile(newReals, from, today, now);
        int unassigned = processor.process(from, today);

        SyncResult result = new SyncResult(now, fetched.size(), created, updated, removed, merge[0], merge[1], unassigned);
        log.info("Sincronização concluída: {}", result);
        return result;
    }

    private void syncAccounts(Instant now) {
        for (DinzoAccount a : dinzo.listAccounts()) {
            TrackedAccount acc = accounts.findById(a.id())
                    .orElseGet(() -> new TrackedAccount(a.id(), a.title(), a.type(), true));
            acc.refresh(a, now);
            accounts.save(acc);
        }
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
            if (pool.isEmpty() || reconciliations.existsByRealIdAndStatusIn(real.getDinzoId(), SETTLED)) {
                continue;
            }
            List<ManualMatcher.Entry> candidates = pool.stream().map(SyncService::toMatch).toList();
            Optional<ManualMatcher.Match> match = matcher.findMatch(toMatch(real), candidates);
            if (match.isEmpty() || reconciliations.existsByManualIdAndRealId(match.get().manualId(), real.getDinzoId())) {
                continue;
            }
            ManualMatcher.Match m = match.get();
            LedgerEntry manual = pool.stream().filter(p -> p.getDinzoId().equals(m.manualId())).findFirst().orElseThrow();
            if (m.automatic()) {
                manual.mergeInto(real.getDinzoId());
                pool.remove(manual);
                reconciliations.save(new Reconciliation(manual.getDinzoId(), real.getDinzoId(), m.score(), ReconciliationStatus.AUTO, now));
                auto++;
            } else {
                reconciliations.save(new Reconciliation(manual.getDinzoId(), real.getDinzoId(), m.score(), ReconciliationStatus.PENDING_REVIEW, now));
                pending++;
            }
        }
        return new int[]{auto, pending};
    }

    private static ManualMatcher.Entry toMatch(LedgerEntry e) {
        return new ManualMatcher.Entry(e.getDinzoId(), e.getAccountId(), e.getDirection(), e.getAmount(),
                e.getDate(), e.getDescription());
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
