package com.maxbenin.financeutils.dinzo;

/**
 * Conta como retornada pelo Dinzo (listar_contas).
 *
 * @param payBillFromAccountId para cartões: a conta corrente que paga a fatura
 */
public record DinzoAccount(String id, String title, String type, String subType, boolean archived,
                           String payBillFromAccountId) {
}
