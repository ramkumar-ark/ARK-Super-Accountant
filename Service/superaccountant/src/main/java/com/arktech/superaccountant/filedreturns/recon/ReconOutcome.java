package com.arktech.superaccountant.filedreturns.recon;

import java.time.Instant;
import java.util.UUID;

public record ReconOutcome(UUID workflowId, ReconStatus status, int material, int info, Instant at) {
}
