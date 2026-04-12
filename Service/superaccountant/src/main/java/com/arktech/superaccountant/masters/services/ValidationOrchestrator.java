package com.arktech.superaccountant.masters.services;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import com.arktech.superaccountant.masters.repository.ValidationRuleConfigRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.rules.ValidationContext;
import com.arktech.superaccountant.masters.rules.ValidationRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ValidationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(ValidationOrchestrator.class);

    @Autowired
    private ValidationRuleConfigRepository ruleConfigRepository;

    @Autowired
    private UploadJobRepository uploadJobRepository;

    @Autowired
    private ValidationFindingRepository findingRepository;

    private final Map<String, ValidationRule> ruleMap;

    public ValidationOrchestrator(List<ValidationRule> rules) {
        this.ruleMap = rules.stream()
                .collect(Collectors.toMap(ValidationRule::getRuleCode, Function.identity()));
    }

    @Transactional
    public UploadJob runAndPersist(UploadJob job, List<ParsedLedger> parsedLedgers,
                                   ValidationContext context) {
        List<ValidationRuleConfig> activeRules = ruleConfigRepository
                .findByActiveTrueOrderByExecutionOrderAsc();

        List<ValidationFinding> allFindings = new ArrayList<>();

        try {
            for (ValidationRuleConfig ruleConfig : activeRules) {
                ValidationRule rule = ruleMap.get(ruleConfig.getRuleCode());
                if (rule == null) {
                    logger.error("No Spring bean found for rule_code: {}", ruleConfig.getRuleCode());
                    job.setStatus(UploadJobStatus.FAILED);
                    job.setErrorMessage("Unknown rule_code: " + ruleConfig.getRuleCode());
                    job.setCompletedAt(Instant.now());
                    uploadJobRepository.save(job);
                    return job;
                }
                List<ValidationFinding> findings = rule.execute(context, parsedLedgers);
                findings.forEach(f -> f.setUploadJobId(job.getId()));
                allFindings.addAll(findings);
            }

            job.setTotalMismatches(allFindings.size());
            job.setStatus(allFindings.isEmpty()
                    ? UploadJobStatus.COMPLETED
                    : UploadJobStatus.COMPLETED_WITH_MISMATCHES);
            job.setCompletedAt(Instant.now());

            uploadJobRepository.save(job);
            findingRepository.saveAll(allFindings);

        } catch (Exception e) {
            logger.error("Validation orchestration failed for job {}: {}", job.getId(), e.getMessage(), e);
            job.setStatus(UploadJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
            uploadJobRepository.save(job);
        }

        return job;
    }
}
