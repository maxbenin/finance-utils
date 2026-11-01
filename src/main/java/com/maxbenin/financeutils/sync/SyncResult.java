package com.maxbenin.financeutils.sync;

import java.time.Instant;

public record SyncResult(Instant at, int fetched, int created, int updated, int removed,
                         int autoMerged, int pendingReview, int unassigned) {
}
