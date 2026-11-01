package com.maxbenin.financeutils.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationRepository extends JpaRepository<Reconciliation, Long> {
    boolean existsByManualIdAndRealId(String manualId, String realId);
    boolean existsByRealIdAndStatusIn(String realId, List<ReconciliationStatus> statuses);
    List<Reconciliation> findByStatus(ReconciliationStatus status);
    List<Reconciliation> findByManualId(String manualId);
}
