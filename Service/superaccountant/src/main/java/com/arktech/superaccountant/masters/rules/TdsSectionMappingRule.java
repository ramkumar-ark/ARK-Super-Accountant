package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that expense ledgers (and purchase accounts, which are expense-like) and fixed asset ledgers have a
 * TDS section assigned. Creditors and every other ledger type are out of scope; scope follows the parent-group chain.
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
            if (!inScope(master)) {
                continue;
            }
            if (master.getTdsSection() == null) {
                FindingSeverity severity = master.getCategory() == LedgerCategory.OTHER
                        ? FindingSeverity.LOW : FindingSeverity.MEDIUM;

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

    private static boolean inScope(PreconfiguredMaster master) {
        if (LedgerGroups.inAny(master, LedgerGroups.GSTIN_PARTY_AND_LOAN_GROUPS)
                || master.getCategory() == LedgerCategory.CREDITOR) {
            return false;
        }
        return LedgerGroups.inAny(master, LedgerGroups.EXPENSE) || LedgerGroups.inAny(master, LedgerGroups.FIXED_ASSETS)
                || master.getCategory() == LedgerCategory.EXPENSE || master.getCategory() == LedgerCategory.PURCHASE;
    }
}
