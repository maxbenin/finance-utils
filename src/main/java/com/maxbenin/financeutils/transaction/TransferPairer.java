package com.maxbenin.financeutils.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Encontra transferências entre contas correntes do app: uma saída numa conta e uma entrada de
 * mesmo valor noutra, em até {@code maxDays} dias. O grupo de transferência informado pela origem
 * (Dinzo) tem prioridade; sem ele, o par só é formado quando não há ambiguidade.
 */
public final class TransferPairer {

    public record Candidate(String id, String accountId, String direction, BigDecimal amount,
                            LocalDate date, String groupRef) {
        boolean debit() {
            return "DEBIT".equals(direction);
        }
    }

    public record Pair(String debitId, String creditId) {
        /** Id do par, igual nos dois lados. */
        public String pairId() {
            return debitId;
        }
    }

    private final int maxDays;

    public TransferPairer(int maxDays) {
        this.maxDays = maxDays;
    }

    public List<Pair> pair(Collection<Candidate> candidates) {
        List<Pair> pairs = new ArrayList<>();
        Set<String> used = new HashSet<>();

        Map<String, List<Candidate>> byGroup = candidates.stream()
                .filter(c -> c.groupRef() != null)
                .collect(Collectors.groupingBy(Candidate::groupRef));
        for (List<Candidate> group : byGroup.values()) {
            List<Candidate> debits = group.stream().filter(Candidate::debit).toList();
            List<Candidate> credits = group.stream().filter(c -> !c.debit()).toList();
            if (debits.size() == 1 && credits.size() == 1 && matches(debits.get(0), credits.get(0))) {
                pairs.add(new Pair(debits.get(0).id(), credits.get(0).id()));
                used.add(debits.get(0).id());
                used.add(credits.get(0).id());
            }
        }

        List<Candidate> debits = candidates.stream()
                .filter(c -> c.debit() && !used.contains(c.id()))
                .sorted(Comparator.comparing(Candidate::date).thenComparing(Candidate::id))
                .toList();
        for (Candidate d : debits) {
            List<Candidate> options = candidates.stream()
                    .filter(c -> !c.debit() && !used.contains(c.id()) && matches(d, c))
                    .toList();
            if (options.isEmpty()) {
                continue;
            }
            long best = options.stream().mapToLong(c -> days(d, c)).min().orElseThrow();
            List<Candidate> closest = options.stream().filter(c -> days(d, c) == best).toList();
            if (closest.size() == 1) {
                Candidate c = closest.get(0);
                pairs.add(new Pair(d.id(), c.id()));
                used.add(d.id());
                used.add(c.id());
            }
        }
        return pairs;
    }

    private boolean matches(Candidate debit, Candidate credit) {
        return !Objects.equals(debit.accountId(), credit.accountId())
                && debit.amount().compareTo(credit.amount()) == 0
                && days(debit, credit) <= maxDays;
    }

    private static long days(Candidate a, Candidate b) {
        return Math.abs(ChronoUnit.DAYS.between(a.date(), b.date()));
    }
}
