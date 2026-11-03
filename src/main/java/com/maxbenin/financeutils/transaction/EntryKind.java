package com.maxbenin.financeutils.transaction;

public enum EntryKind {
    /** Saída da conta corrente ou compra no cartão (estorno no cartão abate). */
    EXPENSE,
    /** Crédito na conta corrente (inclui transferência vinda de outro perfil). */
    INCOME,
    /** Transferência entre contas do mesmo perfil: fora dos totais. */
    TRANSFER,
    /** Pagamento de fatura: fora dos totais (as compras do cartão já contam). */
    BILL_PAYMENT,
    /** Marcado como "não conta nos totais" (acerto de fatura, acerto de contas, reembolsado...). */
    NEUTRAL,
    /** Conta não acompanhada. */
    IGNORED;

    public boolean countsInTotals() {
        return this == EXPENSE || this == INCOME;
    }
}
