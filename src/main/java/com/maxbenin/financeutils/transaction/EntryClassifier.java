package com.maxbenin.financeutils.transaction;

import java.util.Collection;
import java.util.Optional;

/** Classifica o tipo do lançamento para os totais do painel. */
public final class EntryClassifier {

    public static final String FIXED_TAG = "Fixa";

    public record Facts(
            boolean accountTracked,
            boolean creditCardAccount,
            String direction,
            String billToAccountId,
            String transferGroupId,
            boolean hiddenInDinzo,
            String description,
            Long profileId,
            String recurrenceId,
            Integer installmentTotal,
            Collection<String> tags) {
    }

    public record Result(EntryKind kind, Nature nature, String contributor) {
    }

    private final ContributorMatcher contributors;

    public EntryClassifier(ContributorMatcher contributors) {
        this.contributors = contributors;
    }

    public Result classify(Facts f) {
        if (!f.accountTracked()) {
            return new Result(EntryKind.IGNORED, null, null);
        }
        if (f.billToAccountId() != null) {
            return new Result(EntryKind.BILL_PAYMENT, null, null);
        }
        boolean credit = "CREDIT".equals(f.direction());
        if (credit && !f.creditCardAccount()) {
            Optional<String> who = contributors.match(f.description(), f.profileId());
            if (who.isPresent()) {
                return new Result(EntryKind.CONTRIBUTION, null, who.get());
            }
        }
        if (f.transferGroupId() != null) {
            return new Result(EntryKind.TRANSFER, null, null);
        }
        if (f.hiddenInDinzo()) {
            return new Result(EntryKind.IGNORED, null, null);
        }
        if (credit && !f.creditCardAccount()) {
            return new Result(EntryKind.INCOME, null, null);
        }
        return new Result(EntryKind.EXPENSE, nature(f), null);
    }

    private static Nature nature(Facts f) {
        boolean fixedTag = f.tags().stream().anyMatch(t -> t.equalsIgnoreCase(FIXED_TAG));
        boolean installment = f.installmentTotal() != null && f.installmentTotal() > 1;
        return fixedTag || f.recurrenceId() != null || installment ? Nature.FIXED : Nature.VARIABLE;
    }
}
