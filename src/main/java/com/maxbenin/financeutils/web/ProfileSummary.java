package com.maxbenin.financeutils.web;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Totais de um perfil no mês.
 *
 * @param contributionsReceived entradas vindas de outro perfil ou de quem não tem conta no app, por contribuinte
 * @param contributionsMade     gastos que foram transferências para outro perfil, por perfil de destino
 * @param bySubprofile          gastos por subperfil (ex.: Pedro)
 * @param neutral               soma do que ficou fora dos totais por estar marcado "não conta"
 */
public record ProfileSummary(
        String profile,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal fixed,
        BigDecimal variable,
        BigDecimal superfluous,
        Map<String, BigDecimal> contributionsReceived,
        Map<String, BigDecimal> contributionsMade,
        Map<String, BigDecimal> bySubprofile,
        BigDecimal neutral) {

    /** Sobra = Entradas − Gastos. */
    public BigDecimal balance() {
        return income.subtract(expenses);
    }
}
