package com.maxbenin.financeutils.web;

import com.maxbenin.financeutils.account.Account;
import com.maxbenin.financeutils.account.AccountRepository;
import com.maxbenin.financeutils.category.Category;
import com.maxbenin.financeutils.category.CategoryRepository;
import com.maxbenin.financeutils.profile.Profile;
import com.maxbenin.financeutils.profile.ProfileRepository;
import com.maxbenin.financeutils.transaction.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lista do mês, ações sobre cada lançamento e lançamento manual. */
@Controller
@RequestMapping("/lancamentos")
public class TransactionsController {

    /** Linha da lista com os nomes já resolvidos. */
    public record Row(LedgerEntry entry, String account, String category, String profile, String subprofile,
                      String counterpart) {
    }

    private final LedgerEntryRepository entries;
    private final LedgerService ledger;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final ProfileRepository profiles;
    private final Clock clock;

    public TransactionsController(LedgerEntryRepository entries, LedgerService ledger, AccountRepository accounts,
                                  CategoryRepository categories, ProfileRepository profiles, Clock clock) {
        this.entries = entries;
        this.ledger = ledger;
        this.accounts = accounts;
        this.categories = categories;
        this.profiles = profiles;
        this.clock = clock;
    }

    @GetMapping
    public String list(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                       @RequestParam(required = false) Long profile, Model model) {
        YearMonth m = month == null ? YearMonth.now(clock) : month;
        Map<String, String> accountNames = accounts.findAll().stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));
        Map<String, String> categoryNames = categories.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        Map<Long, String> profileNames = profiles.findAll().stream()
                .collect(Collectors.toMap(Profile::getId, Profile::getName));
        List<Row> rows = entries.findEffective(m.atDay(1), m.atEndOfMonth()).stream()
                .filter(e -> profile == null || profile.equals(e.getProfileId()))
                .map(e -> new Row(e, accountNames.get(e.getAccountId()), categoryNames.get(e.getCategoryId()),
                        profileNames.get(e.getProfileId()), profileNames.get(e.getSubprofileId()),
                        profileNames.get(e.getCounterpartProfileId())))
                .toList();
        model.addAttribute("month", m);
        model.addAttribute("previous", m.minusMonths(1));
        model.addAttribute("next", m.plusMonths(1));
        model.addAttribute("profile", profile);
        model.addAttribute("rows", rows);
        addOptions(model);
        return "lancamentos";
    }

    @GetMapping("/novo")
    public String form(Model model) {
        model.addAttribute("today", LocalDate.now(clock));
        addOptions(model);
        return "lancamento-novo";
    }

    @PostMapping
    public String create(@RequestParam String accountId,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         @RequestParam String direction, @RequestParam BigDecimal amount,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String categoryId,
                         @RequestParam(required = false) String cardFinal,
                         @RequestParam(required = false) String tags,
                         @RequestParam(required = false) String notes,
                         RedirectAttributes redirect) {
        List<String> tagList = tags == null ? List.of() : Arrays.stream(tags.split(","))
                .map(String::trim).filter(t -> !t.isEmpty()).toList();
        LedgerEntry e = ledger.createManual(new LedgerService.ManualInput(accountId, date, direction, amount,
                description, categoryId, cardFinal, tagList, notes));
        redirect.addFlashAttribute("message", "Lançamento manual criado.");
        return redirectToMonth(e.getDate());
    }

    @PostMapping("/{id}/totais")
    public String totals(@PathVariable String id, @RequestParam String mode,
                         @RequestParam(required = false) String reason) {
        TotalsOverride override = switch (mode) {
            case "EXCLUDE" -> TotalsOverride.EXCLUDE;
            case "INCLUDE" -> TotalsOverride.INCLUDE;
            default -> null;
        };
        ledger.setTotalsOverride(id, override, reason);
        return back(id);
    }

    @PostMapping("/{id}/superfluo")
    public String superfluous(@PathVariable String id, @RequestParam(required = false) Boolean value) {
        ledger.setSuperfluous(id, value);
        return back(id);
    }

    @PostMapping("/{id}/perfil")
    public String profile(@PathVariable String id, @RequestParam(required = false) Long profileId) {
        ledger.setProfileOverride(id, profileId);
        return back(id);
    }

    @PostMapping("/{id}/categoria")
    public String category(@PathVariable String id, @RequestParam(required = false) String categoryId) {
        ledger.setCategory(id, categoryId);
        return back(id);
    }

    @PostMapping("/{id}/remover")
    public String remove(@PathVariable String id) {
        LocalDate date = entries.findById(id).map(LedgerEntry::getDate).orElse(LocalDate.now(clock));
        ledger.removeManual(id);
        return redirectToMonth(date);
    }

    private void addOptions(Model model) {
        model.addAttribute("accounts", accounts.findByTrackedTrueOrderByName());
        model.addAttribute("categories", categories.findByArchivedFalseOrderByName());
        model.addAttribute("profiles", profiles.findAll().stream()
                .sorted(Comparator.comparing((Profile p) -> p.isRoot() ? p.getName() : p.getParent().getName() + " / " + p.getName()))
                .toList());
    }

    private String back(String id) {
        return redirectToMonth(entries.findById(id).map(LedgerEntry::getDate).orElse(LocalDate.now(clock)));
    }

    private static String redirectToMonth(LocalDate date) {
        return "redirect:/lancamentos?month=" + YearMonth.from(date);
    }
}
