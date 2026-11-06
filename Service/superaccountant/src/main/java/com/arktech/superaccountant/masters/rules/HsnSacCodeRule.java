package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.services.OrganizationSettingsService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that income and expense ledgers that are GST applicable have an HSN/SAC code assigned.
 * The signal is the master's {@code expectedGstApplicable} flag: only {@code true} requires a code, so a ledger
 * with the flag off or unknown is not flagged. GST (Duties &amp; Taxes) ledgers are tax accounts rather than
 * goods or services, party accounts (Sundry Debtors / Creditors) are not income or expense, and PURCHASE, TDS
 * and OTHER ledgers are out of scope.
 * Expense ledgers are skipped when the org turned off REQUIRE_HSN_SAC_ON_EXPENSE (income is unaffected).
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
        // Per-org setting; a missing key means the default, ON.
        boolean requireOnExpense = !Boolean.FALSE.equals(
                context.settings().get(OrganizationSettingsService.REQUIRE_HSN_SAC_ON_EXPENSE));

        for (PreconfiguredMaster master : context.preconfiguredMasters()) {
            boolean requiresHsnSac = (master.getCategory() == LedgerCategory.INCOME
                    || (master.getCategory() == LedgerCategory.EXPENSE && requireOnExpense))
                    && Boolean.TRUE.equals(master.getExpectedGstApplicable())
                    && !isPartyGroup(master.getExpectedParentGroup());

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

    /** Debtor and creditor accounts are parties, whatever category they were filed under. */
    private static boolean isPartyGroup(String parentGroup) {
        if (parentGroup == null) {
            return false;
        }
        String g = parentGroup.trim().toLowerCase();
        return g.equals("sundry debtors") || g.equals("sundry creditors");
    }
}
