package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that expense and income ledgers have a GST applicability type assigned. Party and balance-sheet
 * ledgers (creditors, debtors, loans, assets) are out of scope. Scope follows the ledger's parent-group chain.
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
            if (inScope(master) && master.getGstApplicabilityType() == null) {
                FindingSeverity severity = master.getCategory() == LedgerCategory.INCOME
                        ? FindingSeverity.MEDIUM : FindingSeverity.LOW;

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

    private static boolean inScope(PreconfiguredMaster master) {
        if (LedgerGroups.inAny(master, LedgerGroups.GSTIN_PARTY_AND_LOAN_GROUPS)) {
            return false;
        }
        return LedgerGroups.inAny(master, LedgerGroups.EXPENSE) || LedgerGroups.inAny(master, LedgerGroups.INCOME)
                || master.getCategory() == LedgerCategory.EXPENSE || master.getCategory() == LedgerCategory.INCOME;
    }
}
