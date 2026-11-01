package com.maxbenin.financeutils.dinzo;

/** Conta como retornada pelo Dinzo (listar_contas). */
public record DinzoAccount(String id, String title, String type, String subType, boolean archived) {
}
