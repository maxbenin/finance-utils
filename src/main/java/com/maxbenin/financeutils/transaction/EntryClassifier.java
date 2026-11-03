package com.maxbenin.financeutils.transaction;

import java.util.Collection;

/**
 * Classifica o lançamento para os totais do painel.
 *
 * <p>Regra geral: saída da conta corrente e compra no cartão são gastos; crédito na conta corrente
 * é entrada. Ficam fora: conta não acompanhada, lançamento marcado "não conta", pagamento de fatura
 * e transferência entre contas do mesmo perfil.
 */
public final class EntryClassifier {

    public static final String FIXED_TAG = "Fixa";

    /** Transferência pareada com outra conta do app. */
    public record Transfer(boolean sameProfile, String counterpartProfileName) {
    }

    public record Facts(
            boolean accountTracked,
            boolean creditCardAccount,
            String direction,
            boolean billPayment,
            boolean excludedFromTotals,
            Transfer transfer,
            String description,
            Long profileId,
            String recurrenceRef,
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
        if (f.excludedFromTotals()) {
            return new Result(EntryKind.NEUTRAL, null, null);
        }
        if (f.billPayment()) {
            return new Result(EntryKind.BILL_PAYMENT, null, null);
        }
        if (f.transfer() != null && f.transfer().sameProfile()) {
            return new Result(EntryKind.TRANSFER, null, null);
        }
        if ("CREDIT".equals(f.direction()) && !f.creditCardAccount()) {
            String who = f.transfer() != null
                    ? f.transfer().counterpartProfileName()
                    : contributors.match(f.description(), f.profileId()).orElse(null);
            return new Result(EntryKind.INCOME, null, who);
        }
        return new Result(EntryKind.EXPENSE, nature(f), null);
    }

    private static Nature nature(Facts f) {
        boolean fixedTag = f.tags().stream().anyMatch(t -> t.equalsIgnoreCase(FIXED_TAG));
        boolean installment = f.installmentTotal() != null && f.installmentTotal() > 1;
        return fixedTag || f.recurrenceRef() != null || installment ? Nature.FIXED : Nature.VARIABLE;
    }
}
