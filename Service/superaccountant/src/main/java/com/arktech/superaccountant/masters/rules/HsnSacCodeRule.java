package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that INCOME and GST ledgers have an HSN/SAC code assigned.
 * PURCHASE and EXPENSE ledgers are not required to have HSN/SAC codes.
 * Phase 3 rule: iterates preconfiguredMasters (not parsedLedgers) because
 * Tally JSON does not export HSN/SAC code data.
 */
@Component("HSN_SAC_CODE")
public class HsnSacCodeRule implements ValidationRule {

    @Override
    public String getRuleCode() {
        return "HSN_SAC_CODE";
    }

    @Override
    public List<ValidationFinding> execute(ValidationContext context, List<ParsedLedger> parsedLedgers) {
        List<ValidationFinding> findings = new ArrayList<>();

        for (PreconfiguredMaster master : context.preconfiguredMasters()) {
            boolean requiresHsnSac = master.getCategory() == LedgerCategory.INCOME
                    || master.getCategory() == LedgerCategory.GST;

            if (requiresHsnSac && master.getHsnSacCode() == null) {
                // Note: uploadJobId is injected by ValidationOrchestrator.runAndPersist() before persistence.
                // Do not set it here; it is not available at rule execution time.
                ValidationFinding f = new ValidationFinding();
                f.setRuleCode(getRuleCode());
                f.setLedgerName(master.getLedgerName());
                f.setCategory(master.getCategory());
                f.setSeverity(FindingSeverity.MEDIUM);
                f.setResolveStatus(ResolveStatus.OPEN);
                f.setMessage("Ledger '" + master.getLedgerName() + "' has no HSN/SAC code assigned.");
                f.setSuggestedFix("Assign an HSN code (4-8 digits for goods, e.g., 9954) or SAC code (6 digits for services, e.g., 998311).");
                findings.add(f);
            }
        }

        return findings;
    }
}
