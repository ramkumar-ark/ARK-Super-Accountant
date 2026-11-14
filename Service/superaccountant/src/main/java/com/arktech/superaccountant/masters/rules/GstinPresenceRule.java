package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that registered party and loan ledgers have a GSTIN assigned. In scope: ledgers under Sundry Creditors,
 * Sundry Debtors, Loans &amp; Advances (Asset), Secured Loans and Unsecured Loans, including user-created sub-groups
 * (matched through the parent-group chain), whose GST registration type is something other than
 * Unregistered/Consumer/Unknown or blank. A ledger the user marked "GST not applicable" is skipped.
 * Missing GSTIN on a registered creditor blocks GSTR-2B reconciliation (MSTR-05).
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
            if (inScope(master) && (master.getGstin() == null || master.getGstin().isBlank())) {
                ValidationFinding f = new ValidationFinding();
                f.setRuleCode(getRuleCode());
                f.setLedgerName(master.getLedgerName());
                f.setCategory(master.getCategory());
                f.setSeverity(FindingSeverity.HIGH);
                f.setResolveStatus(ResolveStatus.OPEN);
                f.setMessage("Ledger '" + master.getLedgerName() + "' is GST registered but has no GSTIN. Required for GSTR-2B reconciliation.");
                f.setSuggestedFix("Enter the 15-character GSTIN (e.g., 29ABCDE1234F1Z5), or mark GST as not applicable for this ledger.");
                findings.add(f);
            }
        }

        return findings;
    }

    private static boolean inScope(PreconfiguredMaster master) {
        if (master.getGstApplicabilityType() == GstApplicabilityType.NOT_APPLICABLE) {
            return false; // the user marked GST not applicable through the override flow
        }
        return LedgerGroups.inAny(master, LedgerGroups.GSTIN_PARTY_AND_LOAN_GROUPS) && GstRegistrationTypes.isRegistered(master.getGstRegistrationType());
    }
}
