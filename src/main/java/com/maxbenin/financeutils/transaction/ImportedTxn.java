package com.maxbenin.financeutils.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lançamento como chega de uma origem externa (Dinzo, OFX, CSV...), já com os ids do app
 * (conta e categoria) resolvidos.
 *
 * @param externalId id na origem (id do Dinzo, FITID do OFX...)
 * @param rawData    conteúdo original, para auditoria e detecção de mudanças
 */
public record ImportedTxn(
        String source,
        String externalId,
        String accountId,
        LocalDate date,
        String direction,
        String status,
        BigDecimal amount,
        String description,
        String originalDescription,
        String notes,
        String categoryId,
        String cardFinal,
        Integer installmentCurrent,
        Integer installmentTotal,
        String recurrenceRef,
        boolean manual,
        boolean billPayment,
        String transferGroupRef,
        List<String> tags,
        String rawData) {
}
