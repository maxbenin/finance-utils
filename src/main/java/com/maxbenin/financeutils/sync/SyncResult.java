package com.maxbenin.financeutils.sync;

import java.time.Instant;
import java.time.LocalDate;

/**
 * @param newAccounts   contas que apareceram no Dinzo pela primeira vez (descoberta)
 * @param newCategories categorias criadas a partir do Dinzo
 */
public record SyncResult(Instant at, LocalDate from, LocalDate to, int fetched, int created, int updated,
                         int removed, int autoMerged, int pendingReview, int unassigned,
                         int newAccounts, int newCategories) {
}
