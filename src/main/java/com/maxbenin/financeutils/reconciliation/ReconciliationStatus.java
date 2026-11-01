package com.maxbenin.financeutils.reconciliation;

public enum ReconciliationStatus {
    /** Consolidado automaticamente. */
    AUTO,
    /** Candidato encontrado, aguardando confirmação do usuário. */
    PENDING_REVIEW,
    CONFIRMED,
    REJECTED
}
