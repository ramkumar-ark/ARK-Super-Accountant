package com.arktech.superaccountant.review;

import java.util.UUID;

/**
 * A finding that participates in the re-upload lifecycle. The owning module builds
 * {@link #getFindingKey()} (findingType + "|" + voucherIdentity) and the evidence hash.
 */
public interface LifecycleFinding extends ReviewableFinding {
    String getFindingKey();

    String getEvidenceHash();

    UUID getCarriedFromFindingId();

    void setCarriedFromFindingId(UUID id);
}
