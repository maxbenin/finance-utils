package com.maxbenin.financeutils.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);
    private final SyncService sync;

    public SyncScheduler(SyncService sync) {
        this.sync = sync;
    }

    @Scheduled(cron = "${finance.sync.cron}", zone = "America/Sao_Paulo")
    public void run() {
        try {
            sync.sync();
        } catch (RuntimeException e) {
            log.error("Falha na sincronização agendada com o Dinzo", e);
        }
    }
}
