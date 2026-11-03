package com.maxbenin.financeutils.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param lookbackDays      quantos dias para trás a sincronização periódica reprocessa
 *                          (pendentes viram efetivados, o banco corrige dados)
 * @param initialLoadMonths meses de histórico da carga inicial
 * @param cron              agendamento da sincronização automática ("-" desliga)
 */
@ConfigurationProperties(prefix = "finance.sync")
public record SyncProperties(int lookbackDays, @DefaultValue("12") int initialLoadMonths, String cron) {
}
