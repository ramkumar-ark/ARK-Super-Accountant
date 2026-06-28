package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that CREDITOR (Sundry Creditor/vendor) ledgers have a GSTIN assigned.
 * Missing GSTIN on a creditor ledger blocks GSTR-2B reconciliation (MSTR-05).
 * Phase 3 rule: iterates preconfiguredMasters (not parsedLedgers) because
 * Tally JSON does not export GSTIN data per ledger.
 */
@Component("GSTIN_PRESENCE")
public class GstinPresenceRule implements ValidationRule {

    @Override
    public String getRuleCode() {
        return "GSTIN_PRESENCE";
    }

    @Override
    public List<ValidationFinding> execute(ValidationContext context, List<ParsedLedger> parsedLedgers) {
        List<ValidationFinding> findings = new ArrayList<>();

        for (PreconfiguredMaster master : context.preconfiguredMasters()) {
            if (master == null) {
                continue;
            }
            if (master.getCategory() == LedgerCategory.CREDITOR && (master.getGstin() == null || master.getGstin().isBlank())) {
                ValidationFinding f = new ValidationFinding();
                f.setRuleCode(getRuleCode());
                f.setLedgerName(master.getLedgerName());
                f.setCategory(master.getCategory());
                f.setSeverity(FindingSeverity.HIGH);
                f.setResolveStatus(ResolveStatus.OPEN);
                f.setMessage("Creditor '" + master.getLedgerName() + "' has no GSTIN. Required for GSTR-2B reconciliation.");
                f.setSuggestedFix("Enter the 15-character GSTIN of the vendor (e.g., 29ABCDE1234F1Z5).");
                findings.add(f);
            }
        }

        return findings;
    }
}
