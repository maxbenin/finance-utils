package com.maxbenin.financeutils.transaction;

import com.maxbenin.financeutils.account.Account;
import com.maxbenin.financeutils.account.AccountRepository;
import com.maxbenin.financeutils.profile.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recalcula perfil, transferências e classificação dos lançamentos de um período.
 * Decisões do usuário (perfil escolhido à mão, "não conta nos totais") prevalecem.
 */
@Service
public class EntryProcessor {

    public static final String BILL_SETTLEMENT = "Acerto de fatura";

    private final LedgerEntryRepository entries;
    private final AccountRepository accounts;
    private final ProfileRepository profiles;
    private final ProfileRuleRepository profileRules;
    private final ContributorRuleRepository contributorRules;
    private final ProfileResolver resolver = new ProfileResolver();
    private final TransferPairer pairer = new TransferPairer(1);

    public EntryProcessor(LedgerEntryRepository entries, AccountRepository accounts, ProfileRepository profiles,
                          ProfileRuleRepository profileRules, ContributorRuleRepository contributorRules) {
        this.entries = entries;
        this.accounts = accounts;
        this.profiles = profiles;
        this.profileRules = profileRules;
        this.contributorRules = contributorRules;
    }

    /** @return quantos lançamentos ficaram sem perfil (fila "Avaliar") */
    @Transactional
    public int process(LocalDate from, LocalDate to) {
        RuleSet rules = RuleSet.from(profileRules.findAll());
        EntryClassifier classifier = new EntryClassifier(new ContributorMatcher(
                contributorRules.findAll().stream().map(ContributorMatcher.Rule::of).toList()));
        Map<String, Account> accountById = accounts.findAll().stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Map<Long, Profile> profileById = profiles.findAll().stream()
                .collect(Collectors.toMap(Profile::getId, Function.identity()));
        Set<String> billPayingAccounts = accountById.values().stream()
                .map(Account::getBillPaidFromAccountId).filter(Objects::nonNull).collect(Collectors.toSet());

        // Margem de um dia para parear transferências na borda do período
        List<LedgerEntry> all = entries.findByDateBetween(from.minusDays(1), to.plusDays(1));
        List<LedgerEntry> live = all.stream().filter(e -> !e.isRemoved() && !e.isMerged()).toList();
        Map<String, Set<String>> inherited = inheritedTags(live);

        for (LedgerEntry e : live) {
            Set<String> tags = tagsOf(e, inherited);
            Assignment a = assign(e, tags, rules, profileById);
            e.applyAssignment(a.profileId(), a.subprofileId(), a.rule());
        }

        Map<String, LedgerEntry> byId = live.stream().collect(Collectors.toMap(LedgerEntry::getId, Function.identity()));
        Map<String, TransferPairer.Pair> pairOf = new HashMap<>();
        List<TransferPairer.Candidate> candidates = live.stream()
                .filter(e -> isTransferCandidate(e, accountById))
                .map(e -> new TransferPairer.Candidate(e.getId(), e.getAccountId(), e.getDirection(),
                        e.getAmount(), e.getDate(), e.getTransferGroupRef()))
                .toList();
        for (TransferPairer.Pair p : pairer.pair(candidates)) {
            pairOf.put(p.debitId(), p);
            pairOf.put(p.creditId(), p);
        }

        int unassigned = 0;
        for (LedgerEntry e : live) {
            if (e.getDate().isBefore(from) || e.getDate().isAfter(to)) {
                continue;
            }
            EntryClassifier.Transfer transfer = null;
            TransferPairer.Pair p = pairOf.get(e.getId());
            if (p == null) {
                e.applyTransfer(null, null, null);
            } else {
                LedgerEntry debit = byId.get(p.debitId());
                LedgerEntry credit = byId.get(p.creditId());
                LedgerEntry other = e == debit ? credit : debit;
                boolean sameProfile = Objects.equals(debit.getProfileId(), credit.getProfileId());
                String auto = !sameProfile && billPayingAccounts.contains(credit.getAccountId()) ? BILL_SETTLEMENT : null;
                e.applyTransfer(p.pairId(), sameProfile ? null : other.getProfileId(), auto);
                Profile counterpart = other.getProfileId() == null ? null : profileById.get(other.getProfileId());
                transfer = new EntryClassifier.Transfer(sameProfile, counterpart == null ? null : counterpart.getName());
            }

            Account acc = accountById.get(e.getAccountId());
            EntryClassifier.Result r = classifier.classify(new EntryClassifier.Facts(
                    acc == null || acc.isTracked(),
                    acc != null && acc.isCreditCard(),
                    e.getDirection(),
                    e.isBillPayment(),
                    e.isExcludedFromTotals(),
                    transfer,
                    e.getDescription(),
                    e.getProfileId(),
                    e.getRecurrenceRef(),
                    e.getInstallmentTotal(),
                    tagsOf(e, inherited)));
            e.applyClassification(r.kind(), r.nature(), r.contributor());

            if (e.getProfileId() == null && r.kind() != EntryKind.IGNORED) {
                unassigned++;
            }
        }
        return unassigned;
    }

    private Assignment assign(LedgerEntry e, Set<String> tags, RuleSet rules, Map<Long, Profile> profileById) {
        Long override = e.getProfileOverrideId();
        if (override != null && profileById.containsKey(override)) {
            Profile chosen = profileById.get(override);
            if (!chosen.isRoot()) {
                return new Assignment(chosen.getParent().getId(), chosen.getId(), "USER");
            }
            Assignment byTags = resolver.resolve(new ProfileResolver.Facts(null, null, true, tags), rules);
            Long sub = Objects.equals(byTags.profileId(), chosen.getId()) ? byTags.subprofileId() : null;
            return new Assignment(chosen.getId(), sub, "USER");
        }
        return resolver.resolve(new ProfileResolver.Facts(e.getAccountId(), e.getCardFinal(), e.isManual(), tags), rules);
    }

    private static boolean isTransferCandidate(LedgerEntry e, Map<String, Account> accountById) {
        Account acc = accountById.get(e.getAccountId());
        return acc != null && acc.isTracked() && !acc.isCreditCard() && !e.isManual() && !e.isBillPayment();
    }

    private static Set<String> tagsOf(LedgerEntry e, Map<String, Set<String>> inherited) {
        Set<String> tags = new LinkedHashSet<>(e.getTags());
        tags.addAll(inherited.getOrDefault(e.getId(), Set.of()));
        return tags;
    }

    /** O lançamento real herda as tags dos manuais consolidados nele (ex.: subperfil "Pedro"). */
    private Map<String, Set<String>> inheritedTags(List<LedgerEntry> list) {
        Set<String> ids = list.stream().filter(e -> !e.isManual()).map(LedgerEntry::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> result = new HashMap<>();
        for (LedgerEntry manual : entries.findByMergedIntoIdIn(ids)) {
            result.computeIfAbsent(manual.getMergedIntoId(), k -> new LinkedHashSet<>()).addAll(manual.getTags());
        }
        return result;
    }
}
