package com.maxbenin.financeutils.reconciliation;

import com.maxbenin.financeutils.transaction.EntryProcessor;
import com.maxbenin.financeutils.transaction.LedgerEntry;
import com.maxbenin.financeutils.transaction.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** Decisões do usuário sobre consolidações pendentes. No Dinzo nada é alterado. */
@Service
public class ReconciliationService {

    private final ReconciliationRepository reconciliations;
    private final LedgerEntryRepository entries;
    private final EntryProcessor processor;
    private final Clock clock;

    public ReconciliationService(ReconciliationRepository reconciliations, LedgerEntryRepository entries,
                                 EntryProcessor processor, Clock clock) {
        this.reconciliations = reconciliations;
        this.entries = entries;
        this.processor = processor;
        this.clock = clock;
    }

    public List<Reconciliation> pending() {
        return reconciliations.findByStatus(ReconciliationStatus.PENDING_REVIEW);
    }

    @Transactional
    public void decide(long id, boolean accept) {
        Reconciliation r = reconciliations.findById(id).orElseThrow();
        r.decide(accept, clock.instant());
        if (accept) {
            LedgerEntry manual = entries.findById(r.getManualId()).orElseThrow();
            LedgerEntry real = entries.findById(r.getRealId()).orElseThrow();
            manual.mergeInto(real.getDinzoId());
            // Outros candidatos do mesmo manual deixam de valer
            reconciliations.findByManualId(manual.getDinzoId()).stream()
                    .filter(o -> !o.getId().equals(r.getId()) && o.getStatus() == ReconciliationStatus.PENDING_REVIEW)
                    .forEach(o -> o.decide(false, clock.instant()));
            entries.flush();
            processor.process(real.getDate().minusDays(1), real.getDate().plusDays(1));
        }
    }
}
