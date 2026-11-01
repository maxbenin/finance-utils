package com.maxbenin.financeutils.web;

import com.maxbenin.financeutils.profile.Profile;
import com.maxbenin.financeutils.profile.ProfileRepository;
import com.maxbenin.financeutils.transaction.EntryKind;
import com.maxbenin.financeutils.transaction.LedgerEntry;
import com.maxbenin.financeutils.transaction.LedgerEntryRepository;
import com.maxbenin.financeutils.transaction.Nature;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Painel por perfil, por mês calendário e data da compra (competência). */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final LedgerEntryRepository entries;
    private final ProfileRepository profiles;

    public DashboardService(LedgerEntryRepository entries, ProfileRepository profiles) {
        this.entries = entries;
        this.profiles = profiles;
    }

    public List<ProfileSummary> summaries(YearMonth month) {
        List<LedgerEntry> list = entries.findEffective(month.atDay(1), month.atEndOfMonth());
        Map<Long, String> names = profiles.findAll().stream()
                .collect(Collectors.toMap(Profile::getId, Profile::getName));

        List<ProfileSummary> result = new ArrayList<>();
        for (Profile p : profiles.findByParentIsNullAndActiveTrueOrderByName()) {
            List<LedgerEntry> mine = list.stream().filter(e -> p.getId().equals(e.getProfileId())).toList();
            List<LedgerEntry> expenses = mine.stream().filter(e -> e.getKind() == EntryKind.EXPENSE).toList();

            Map<String, BigDecimal> received = mine.stream()
                    .filter(e -> e.getKind() == EntryKind.CONTRIBUTION)
                    .collect(Collectors.groupingBy(LedgerEntry::getContributor, TreeMap::new,
                            Collectors.reducing(BigDecimal.ZERO, LedgerEntry::getAmount, BigDecimal::add)));
            BigDecimal made = sum(list.stream()
                    .filter(e -> e.getKind() == EntryKind.CONTRIBUTION)
                    .filter(e -> p.getName().equalsIgnoreCase(e.getContributor()))
                    .filter(e -> !p.getId().equals(e.getProfileId()))
                    .map(LedgerEntry::getAmount).toList());

            Map<String, BigDecimal> bySub = expenses.stream()
                    .filter(e -> e.getSubprofileId() != null)
                    .collect(Collectors.groupingBy(e -> names.get(e.getSubprofileId()), TreeMap::new,
                            Collectors.reducing(BigDecimal.ZERO, LedgerEntry::signedExpense, BigDecimal::add)));

            result.add(new ProfileSummary(
                    p.getName(),
                    sum(mine.stream().filter(e -> e.getKind() == EntryKind.INCOME).map(LedgerEntry::getAmount).toList()),
                    expenseSum(expenses, e -> true),
                    expenseSum(expenses, e -> e.getNature() == Nature.FIXED),
                    expenseSum(expenses, e -> e.getNature() == Nature.VARIABLE),
                    expenseSum(expenses, e -> Boolean.TRUE.equals(e.getSuperfluous())),
                    received,
                    made,
                    bySub));
        }
        return result;
    }

    /** Lançamentos sem perfil, que precisam de uma regra ou de ajuste manual. */
    public List<LedgerEntry> unassigned(YearMonth month) {
        return entries.findEffective(month.atDay(1), month.atEndOfMonth()).stream()
                .filter(e -> e.getProfileId() == null && e.getKind() != EntryKind.IGNORED)
                .toList();
    }

    private static BigDecimal expenseSum(List<LedgerEntry> expenses, Predicate<LedgerEntry> filter) {
        return sum(expenses.stream().filter(filter).map(LedgerEntry::signedExpense).toList());
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
