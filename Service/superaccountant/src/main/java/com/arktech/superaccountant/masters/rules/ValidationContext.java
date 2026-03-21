package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.models.PreconfiguredMaster;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ValidationContext(
        UUID organizationId,
        String uploadedBy,
        List<PreconfiguredMaster> preconfiguredMasters,
        Map<String, Object> settings
) {}
