package com.arktech.superaccountant.workflow.snapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Seam for resolving where a tampered voucher originally appeared in an
 * affected workflow's prepared return. Implemented outside the workflow
 * package (by gstr1) so workflow never imports gstr1.
 */
public interface AmendmentOriginSource {

    Optional<AmendmentOrigin> origin(UUID workflowId, String voucherKey);

    record AmendmentOrigin(String tableCode, String docKey) {
    }
}
