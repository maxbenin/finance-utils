package com.maxbenin.financeutils.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param lookbackDays quantos dias para trás são reprocessados a cada sincronização
 *                     (pendentes viram efetivados, o banco corrige dados)
 * @param cron         agendamento da sincronização automática ("-" desliga)
 */
@ConfigurationProperties(prefix = "finance.sync")
public record SyncProperties(int lookbackDays, String cron) {
}
