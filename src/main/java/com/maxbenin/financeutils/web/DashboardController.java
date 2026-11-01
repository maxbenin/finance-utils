package com.maxbenin.financeutils.web;

import com.maxbenin.financeutils.reconciliation.ReconciliationService;
import com.maxbenin.financeutils.sync.SyncResult;
import com.maxbenin.financeutils.sync.SyncService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.YearMonth;

@Controller
public class DashboardController {

    private final DashboardService dashboard;
    private final SyncService sync;
    private final ReconciliationService reconciliation;
    private final Clock clock;

    public DashboardController(DashboardService dashboard, SyncService sync,
                               ReconciliationService reconciliation, Clock clock) {
        this.dashboard = dashboard;
        this.sync = sync;
        this.reconciliation = reconciliation;
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
        model.addAttribute("pending", reconciliation.pending());
        return "dashboard";
    }

    @PostMapping("/sync")
    public String sync(RedirectAttributes redirect) {
        SyncResult r = sync.sync();
        redirect.addFlashAttribute("syncResult", r);
        return "redirect:/";
    }

    @PostMapping("/consolidacoes/{id}")
    public String decide(@PathVariable long id, @RequestParam boolean accept) {
        reconciliation.decide(id, accept);
        return "redirect:/";
    }
}
