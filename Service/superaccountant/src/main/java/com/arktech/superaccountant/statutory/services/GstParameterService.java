package com.arktech.superaccountant.statutory.services;

import com.arktech.superaccountant.statutory.models.Statute;
import com.arktech.superaccountant.statutory.models.StatutorySection;
import com.arktech.superaccountant.statutory.models.StatutorySectionRule;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRepository;
import com.arktech.superaccountant.statutory.repository.StatutorySectionRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the global CGST parameter rows. No cache: a few dozen rows, read once per
 * prepare / validate / recon run, so a seed change is live on the next run.
 */
@Service
public class GstParameterService implements GstParameterSource {

    private final StatutorySectionRepository sectionRepository;

    private final StatutorySectionRuleRepository ruleRepository;

    public GstParameterService(StatutorySectionRepository sectionRepository,
                               StatutorySectionRuleRepository ruleRepository) {
        this.sectionRepository = sectionRepository;
        this.ruleRepository = ruleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public GstParameters current() {
        Map<String, List<StatutorySectionRule>> byCode = new HashMap<>();
        for (StatutorySection section : sectionRepository.findByStatute(Statute.CGST)) {
            if (Boolean.TRUE.equals(section.getActive())) {
                byCode.put(section.getCode(), ruleRepository.findBySectionId(section.getId()));
            }
        }
        return GstParameters.of(byCode);
    }
}
