package com.maxbenin.financeutils.transaction;

import com.maxbenin.financeutils.account.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.NoSuchElementException;

/** Ações do usuário sobre lançamentos. Cada ação recalcula os lançamentos afetados. */
@Service
public class LedgerService {

    private final LedgerEntryRepository entries;
    private final AccountRepository accounts;
    private final EntryProcessor processor;
    private final Clock clock;

    public LedgerService(LedgerEntryRepository entries, AccountRepository accounts,
                         EntryProcessor processor, Clock clock) {
        this.entries = entries;
        this.accounts = accounts;
        this.processor = processor;
        this.clock = clock;
    }

    public record ManualInput(String accountId, LocalDate date, String direction, BigDecimal amount,
                              String description, String categoryId, String cardFinal,
                              Collection<String> tags, String notes) {
    }

    @Transactional
    public LedgerEntry createManual(ManualInput in) {
        if (!accounts.existsById(in.accountId())) {
            throw new IllegalArgumentException("Conta inexistente: " + in.accountId());
        }
        if (in.amount() == null || in.amount().signum() <= 0) {
            throw new IllegalArgumentException("O valor deve ser positivo; a direção diz se é entrada ou saída");
        }
        LedgerEntry e = entries.save(LedgerEntry.manual(in.accountId(), in.date(), in.direction(), in.amount(),
                blankToNull(in.description()), blankToNull(in.categoryId()), blankToNull(in.cardFinal()),
                in.tags(), blankToNull(in.notes()), clock.instant()));
        reprocess(e);
        return e;
    }

    /** Remove um lançamento manual feito no app (lançamentos importados não são apagados). */
    @Transactional
    public void removeManual(String id) {
        LedgerEntry e = get(id);
        if (!LedgerEntry.ORIGIN_APP.equals(e.getOrigin())) {
            throw new IllegalStateException("Só lançamentos feitos no app podem ser removidos");
        }
        e.markRemoved(clock.instant());
        reprocess(e);
    }

    /** {@code override} nulo volta à sugestão automática. */
    @Transactional
    public void setTotalsOverride(String id, TotalsOverride override, String reason) {
        LedgerEntry e = get(id);
        e.setTotalsOverride(override, blankToNull(reason), clock.instant());
        reprocess(e);
    }

    @Transactional
    public void setSuperfluous(String id, Boolean superfluous) {
        get(id).setSuperfluous(superfluous, clock.instant());
    }

    /** {@code profileId} nulo volta às regras automáticas. */
    @Transactional
    public void setProfileOverride(String id, Long profileId) {
        LedgerEntry e = get(id);
        e.setProfileOverrideId(profileId, clock.instant());
        reprocess(e);
    }

    @Transactional
    public void setCategory(String id, String categoryId) {
        get(id).setCategoryId(blankToNull(categoryId), clock.instant());
    }

    private LedgerEntry get(String id) {
        return entries.findById(id).orElseThrow(() -> new NoSuchElementException("Lançamento não encontrado: " + id));
    }

    private void reprocess(LedgerEntry e) {
        entries.flush();
        processor.process(e.getDate().minusDays(1), e.getDate().plusDays(1));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
