package com.maxbenin.financeutils.dinzo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lançamento como retornado pelo Dinzo (listar_lancamentos, visão ledger).
 * {@code rawJson} guarda o objeto original para auditoria e detecção de mudanças.
 */
public record DinzoTransaction(
        String id,
        boolean projected,
        LocalDate date,
        String type,
        String status,
        BigDecimal amount,
        String description,
        String originalDescription,
        String notes,
        String accountId,
        String billToAccountId,
        String categoryId,
        List<String> tags,
        String recurrenceId,
        Integer installmentCurrent,
        Integer installmentTotal,
        String transferGroupId,
        String source,
        boolean hideFromResults,
        String cardNumber,
        String rawJson) {

    public boolean isManual() {
        return "APP_MANUAL".equals(source);
    }

    public boolean isOpenFinance() {
        return "OPEN_FINANCE".equals(source);
    }
}
