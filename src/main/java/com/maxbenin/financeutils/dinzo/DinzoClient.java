package com.maxbenin.financeutils.dinzo;

import java.time.LocalDate;
import java.util.List;

/**
 * Fonte dos dados do Dinzo. Isola o restante da aplicação do meio de acesso
 * (MCP hoje; REST se o Dinzo disponibilizar).
 */
public interface DinzoClient {

    List<DinzoAccount> listAccounts();

    /** Lançamentos reais (sem projeções) na visão ledger, cada gasto de cartão individualizado. */
    List<DinzoTransaction> listTransactions(LocalDate from, LocalDate to);
}
