package com.arktech.superaccountant.workflow.seam;

import java.util.List;
import java.util.UUID;

/**
 * Company-level reasons that block financial-year close or baseline
 * confirmation. Implementations are optional beans; an empty list means no
 * blocker.
 */
public interface CompanyBlockerSource {

    List<String> financialYearCloseBlockers(UUID organizationId, int financialYear);

    List<String> baselineConfirmBlockers(UUID organizationId);
}
