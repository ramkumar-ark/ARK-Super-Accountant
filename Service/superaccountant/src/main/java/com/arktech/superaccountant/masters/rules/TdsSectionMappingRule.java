package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that every PreconfiguredMaster has a TDS section assigned.
 * Phase 3 rule: iterates preconfiguredMasters (not parsedLedgers) because
 * Tally JSON does not export TDS section data.
 */
@Component("TDS_SECTION_MAPPING")
public class TdsSectionMappingRule implements ValidationRule {

    @Override
    public String getRuleCode() {
        return "TDS_SECTION_MAPPING";
    }

    @Override
    public List<ValidationFinding> execute(ValidationContext context, List<ParsedLedger> parsedLedgers) {
        List<ValidationFinding> findings = new ArrayList<>();

        for (PreconfiguredMaster master : context.preconfiguredMasters()) {
            // GST, TDS, and OTHER category ledgers are structurally not subject to TDS — skip them
            if (master.getCategory() == LedgerCategory.GST
                    || master.getCategory() == LedgerCategory.TDS
                    || master.getCategory() == LedgerCategory.OTHER) {
                continue;
            }
            if (master.getTdsSection() == null) {
                FindingSeverity severity = switch (master.getCategory()) {
                    case TDS -> FindingSeverity.HIGH;
                    case PURCHASE, EXPENSE, INCOME -> FindingSeverity.MEDIUM;
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
                f.setMessage("Ledger '" + master.getLedgerName() + "' has no TDS section assigned.");
                f.setSuggestedFix("Assign a TDS section (e.g., 194C, 194J_A) or mark as 'NOT_SUBJECT' if not applicable to TDS.");
                findings.add(f);
            }
        }

        return findings;
    }
}
