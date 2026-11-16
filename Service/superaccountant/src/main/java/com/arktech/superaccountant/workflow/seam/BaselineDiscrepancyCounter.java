package com.arktech.superaccountant.workflow.seam;

import java.util.UUID;

/**
 * Live count of open material filed-return discrepancies for a company, computed on demand so
 * explanations are reflected immediately. Returns null when the count is not available (mapping
 * incomplete or nothing imported yet); the UI treats null as "not available", not zero.
 */
public interface BaselineDiscrepancyCounter {

    Integer openMaterialCount(UUID organizationId);
}
