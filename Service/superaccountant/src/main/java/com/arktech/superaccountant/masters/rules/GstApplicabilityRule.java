package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that every PreconfiguredMaster has a GST applicability type assigned.
 * Phase 3 rule: iterates preconfiguredMasters (not parsedLedgers) because
 * Tally JSON does not export GST applicability type data.
 */
@Component("GST_APPLICABILITY")
public class GstApplicabilityRule implements ValidationRule {

    @Override
    public String getRuleCode() {
        return "GST_APPLICABILITY";
    }

    @Override
    public List<ValidationFinding> execute(ValidationContext context, List<ParsedLedger> parsedLedgers) {
        List<ValidationFinding> findings = new ArrayList<>();

        for (PreconfiguredMaster master : context.preconfiguredMasters()) {
            if (master.getGstApplicabilityType() == null) {
                FindingSeverity severity = switch (master.getCategory()) {
                    case INCOME, GST -> FindingSeverity.MEDIUM;
                    default -> FindingSeverity.LOW;
                };

                // Note: uploadJobId is injected by ValidationOrchestrator.runAndPersist() before persistence.
                // Do not set it here; it is not available at rule execution time.
                ValidationFinding f = new ValidationFinding();
                f.setRuleCode(getRuleCode());
                f.setLedgerName(master.getLedgerName());
                f.setCategory(master.getCategory());
                f.setSeverity(severity);
                f.setResolveStatus(ResolveStatus.OPEN);
                f.setMessage("Ledger '" + master.getLedgerName() + "' has no GST applicability type assigned.");
                f.setSuggestedFix("Assign a GST applicability type (e.g., TAXABLE, EXEMPT, ZERO_RATED, NON_GST, RCM, or NOT_APPLICABLE).");
                findings.add(f);
            }
        }

        return findings;
    }
}
