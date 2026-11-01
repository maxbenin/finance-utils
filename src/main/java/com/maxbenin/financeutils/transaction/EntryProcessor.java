package com.maxbenin.financeutils.transaction;

import com.maxbenin.financeutils.profile.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Aplica atribuição de perfil e classificação a todos os lançamentos de um período. */
@Service
public class EntryProcessor {

    private final LedgerEntryRepository entries;
    private final TrackedAccountRepository accounts;
    private final ProfileRuleRepository profileRules;
    private final ContributorRuleRepository contributorRules;
    private final ProfileResolver resolver = new ProfileResolver();

    public EntryProcessor(LedgerEntryRepository entries, TrackedAccountRepository accounts,
                          ProfileRuleRepository profileRules, ContributorRuleRepository contributorRules) {
        this.entries = entries;
        this.accounts = accounts;
        this.profileRules = profileRules;
        this.contributorRules = contributorRules;
    }

    /** @return quantos lançamentos ficaram sem perfil (fila "Avaliar") */
    @Transactional
    public int process(LocalDate from, LocalDate to) {
        RuleSet rules = RuleSet.from(profileRules.findAll());
        EntryClassifier classifier = new EntryClassifier(new ContributorMatcher(
                contributorRules.findAll().stream().map(ContributorMatcher.Rule::of).toList()));
        Map<String, TrackedAccount> accountById = accounts.findAll().stream()
                .collect(Collectors.toMap(TrackedAccount::getDinzoId, Function.identity()));

        List<LedgerEntry> list = entries.findByDateBetween(from, to);
        Map<String, Set<String>> inheritedTags = inheritedTags(list);

        int unassigned = 0;
        for (LedgerEntry e : list) {
            Set<String> tags = new LinkedHashSet<>(e.getTags());
            tags.addAll(inheritedTags.getOrDefault(e.getDinzoId(), Set.of()));

            Assignment a = resolver.resolve(
                    new ProfileResolver.Facts(e.getAccountId(), e.getCardFinal(), e.isManual(), tags), rules);
            e.applyAssignment(a.profileId(), a.subprofileId(), a.rule());

            TrackedAccount acc = accountById.get(e.getAccountId());
            EntryClassifier.Result r = classifier.classify(new EntryClassifier.Facts(
                    acc == null || acc.isTracked(),
                    acc != null && acc.isCreditCard(),
                    e.getDirection(),
                    e.getBillToAccountId(),
                    e.getTransferGroupId(),
                    e.isHiddenInDinzo(),
                    e.getDescription(),
                    a.profileId(),
                    e.getRecurrenceId(),
                    e.getInstallmentTotal(),
                    tags));
            e.applyClassification(r.kind(), r.nature(), r.contributor());

            if (!a.isAssigned() && r.kind() != EntryKind.IGNORED && !e.isMerged()) {
                unassigned++;
            }
        }
        return unassigned;
    }

    /** O lançamento real herda as tags dos manuais consolidados nele (ex.: subperfil "Pedro"). */
    private Map<String, Set<String>> inheritedTags(List<LedgerEntry> list) {
        Set<String> ids = list.stream().filter(LedgerEntry::isOpenFinance)
                .map(LedgerEntry::getDinzoId).collect(Collectors.toSet());
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
