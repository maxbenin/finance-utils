package com.maxbenin.financeutils.web;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Totais de um perfil no mês.
 *
 * @param contributionsReceived depósitos de membros nas contas deste perfil, por contribuinte
 * @param contributionsMade     quanto este perfil depositou nas contas de outros perfis
 * @param bySubprofile          gastos por subperfil (ex.: Pedro)
 */
public record ProfileSummary(
        String profile,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal fixed,
        BigDecimal variable,
        BigDecimal superfluous,
        Map<String, BigDecimal> contributionsReceived,
        BigDecimal contributionsMade,
        Map<String, BigDecimal> bySubprofile) {

    /** Sobra = entradas + contribuições recebidas − gastos − contribuições feitas. */
    public BigDecimal balance() {
        BigDecimal received = contributionsReceived.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return income.add(received).subtract(expenses).subtract(contributionsMade);
    }
}
