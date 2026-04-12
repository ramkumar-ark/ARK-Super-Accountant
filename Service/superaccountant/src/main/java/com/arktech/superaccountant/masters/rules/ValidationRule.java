package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.ValidationFinding;

import java.util.List;

/**
 * Strategy interface for validation rules.
 * Each implementation is a Spring @Component with getRuleCode() matching
 * the rule_code value stored in the validation_rules table.
 */
public interface ValidationRule {
    String getRuleCode();
    List<ValidationFinding> execute(ValidationContext context, List<ParsedLedger> parsedLedgers);
}
