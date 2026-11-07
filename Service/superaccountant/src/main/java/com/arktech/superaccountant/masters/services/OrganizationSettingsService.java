package com.arktech.superaccountant.masters.services;

import com.arktech.superaccountant.masters.models.OrganizationSetting;
import com.arktech.superaccountant.masters.repository.OrganizationSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Reads and writes per-organization settings. A setting with no stored row has its default value. */
@Service
public class OrganizationSettingsService {

    /** Key of "Require HSN/SAC on GST-applicable expense ledgers". Default ON. */
    public static final String REQUIRE_HSN_SAC_ON_EXPENSE = "REQUIRE_HSN_SAC_ON_EXPENSE";

    @Autowired
    private OrganizationSettingRepository repository;

    public boolean requireHsnSacOnExpense(UUID orgId) {
        return repository.findByOrganizationIdAndSettingKey(orgId, REQUIRE_HSN_SAC_ON_EXPENSE)
                .map(s -> !"false".equalsIgnoreCase(s.getSettingValue()))
                .orElse(true);
    }

    public OrganizationSetting find(UUID orgId, String key) {
        return repository.findByOrganizationIdAndSettingKey(orgId, key).orElse(null);
    }

    @Transactional
    public OrganizationSetting setRequireHsnSacOnExpense(UUID orgId, boolean value, String username) {
        OrganizationSetting s = repository.findByOrganizationIdAndSettingKey(orgId, REQUIRE_HSN_SAC_ON_EXPENSE)
                .orElseGet(() -> {
                    OrganizationSetting n = new OrganizationSetting();
                    n.setOrganizationId(orgId);
                    n.setSettingKey(REQUIRE_HSN_SAC_ON_EXPENSE);
                    return n;
                });
        s.setSettingValue(Boolean.toString(value));
        s.setUpdatedBy(username);
        s.setUpdatedAt(Instant.now());
        return repository.save(s);
    }

    /** Settings handed to validation rules via {@code ValidationContext.settings()}; defaults filled in. */
    public Map<String, Object> validationSettings(UUID orgId) {
        Map<String, Object> m = new HashMap<>();
        m.put(REQUIRE_HSN_SAC_ON_EXPENSE, requireHsnSacOnExpense(orgId));
        return m;
    }
}
