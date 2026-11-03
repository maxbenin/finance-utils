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
import java.util.function.Function;
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
            List<LedgerEntry> income = mine.stream().filter(e -> e.getKind() == EntryKind.INCOME).toList();

            result.add(new ProfileSummary(
                    p.getName(),
                    sum(income, LedgerEntry::getAmount),
                    sum(expenses, LedgerEntry::signedExpense),
                    sum(filter(expenses, e -> e.getNature() == Nature.FIXED), LedgerEntry::signedExpense),
                    sum(filter(expenses, e -> e.getNature() == Nature.VARIABLE), LedgerEntry::signedExpense),
                    sum(filter(expenses, e -> Boolean.TRUE.equals(e.getSuperfluous())), LedgerEntry::signedExpense),
                    group(filter(income, e -> e.getContributor() != null), LedgerEntry::getContributor, LedgerEntry::getAmount),
                    group(filter(expenses, e -> e.getCounterpartProfileId() != null),
                            e -> names.get(e.getCounterpartProfileId()), LedgerEntry::getAmount),
                    group(filter(expenses, e -> e.getSubprofileId() != null),
                            e -> names.get(e.getSubprofileId()), LedgerEntry::signedExpense),
                    sum(filter(mine, e -> e.getKind() == EntryKind.NEUTRAL), LedgerEntry::getAmount)));
        }
        return result;
    }

    /** Lançamentos sem perfil, que precisam de uma regra ou de escolha manual. */
    public List<LedgerEntry> unassigned(YearMonth month) {
        return filter(entries.findEffective(month.atDay(1), month.atEndOfMonth()),
                e -> e.getProfileId() == null && e.getKind() != EntryKind.IGNORED);
    }

    private static List<LedgerEntry> filter(List<LedgerEntry> list, Predicate<LedgerEntry> p) {
        return list.stream().filter(p).toList();
    }

    private static BigDecimal sum(List<LedgerEntry> list, Function<LedgerEntry, BigDecimal> value) {
        return list.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<String, BigDecimal> group(List<LedgerEntry> list, Function<LedgerEntry, String> key,
                                                 Function<LedgerEntry, BigDecimal> value) {
        return list.stream().collect(Collectors.groupingBy(key, TreeMap::new,
                Collectors.reducing(BigDecimal.ZERO, value, BigDecimal::add)));
    }
}
