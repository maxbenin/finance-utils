package com.maxbenin.financeutils.web;

import com.maxbenin.financeutils.reconciliation.Reconciliation;
import com.maxbenin.financeutils.reconciliation.ReconciliationService;
import com.maxbenin.financeutils.sync.SyncResult;
import com.maxbenin.financeutils.sync.SyncService;
import com.maxbenin.financeutils.transaction.LedgerEntry;
import com.maxbenin.financeutils.transaction.LedgerEntryRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

@Controller
public class DashboardController {

    /** Consolidação pendente com os dois lançamentos, para a tela. */
    public record PendingView(Reconciliation reconciliation, LedgerEntry manual, LedgerEntry real) {
    }

    private final DashboardService dashboard;
    private final SyncService sync;
    private final ReconciliationService reconciliation;
    private final LedgerEntryRepository entries;
    private final Clock clock;

    public DashboardController(DashboardService dashboard, SyncService sync, ReconciliationService reconciliation,
                               LedgerEntryRepository entries, Clock clock) {
        this.dashboard = dashboard;
        this.sync = sync;
        this.reconciliation = reconciliation;
        this.entries = entries;
        this.clock = clock;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                        Model model) {
        YearMonth m = month == null ? YearMonth.now(clock) : month;
        model.addAttribute("month", m);
        model.addAttribute("previous", m.minusMonths(1));
        model.addAttribute("next", m.plusMonths(1));
        model.addAttribute("summaries", dashboard.summaries(m));
        model.addAttribute("unassigned", dashboard.unassigned(m));
        List<PendingView> pending = reconciliation.pending().stream()
                .map(r -> new PendingView(r, entries.findById(r.getManualId()).orElse(null),
                        entries.findById(r.getRealId()).orElse(null)))
                .toList();
        model.addAttribute("pending", pending);
        return "dashboard";
    }

    @PostMapping("/sync")
    public String sync(RedirectAttributes redirect) {
        SyncResult r = sync.sync();
        redirect.addFlashAttribute("syncResult", r);
        return "redirect:/";
    }

    @PostMapping("/sync/inicial")
    public String initialLoad(RedirectAttributes redirect) {
        SyncResult r = sync.initialLoad();
        redirect.addFlashAttribute("syncResult", r);
        return "redirect:/";
    }

    @PostMapping("/consolidacoes/{id}")
    public String decide(@PathVariable long id, @RequestParam boolean accept) {
        reconciliation.decide(id, accept);
        return "redirect:/";
    }
}
