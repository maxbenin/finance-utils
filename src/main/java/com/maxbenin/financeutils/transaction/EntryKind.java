package com.maxbenin.financeutils.transaction;

public enum EntryKind {
    /** Gasto (débito em conta ou cartão; crédito no cartão = estorno, reduz o gasto). */
    EXPENSE,
    /** Entrada em conta corrente. */
    INCOME,
    /** Depósito de um membro numa conta de outro perfil (ex.: Max -> BB/Familia). */
    CONTRIBUTION,
    /** Transferência entre contas próprias: fora dos totais. */
    TRANSFER,
    /** Pagamento de fatura: fora dos totais (os gastos já contam individualmente). */
    BILL_PAYMENT,
    /** Conta não acompanhada ou lançamento oculto no Dinzo. */
    IGNORED
}
