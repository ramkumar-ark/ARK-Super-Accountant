package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.ValidationFinding;
import com.arktech.superaccountant.masters.pan.PanFormat;
import com.arktech.superaccountant.masters.pan.PanOverrideGateway;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component("PAN_FORMAT")
public class PanFormatRule implements ValidationRule {

    private final PanOverrideGateway panOverrides;

    public PanFormatRule(PanOverrideGateway panOverrides) {
        this.panOverrides = panOverrides;
    }

    @Override
    public String getRuleCode() {
        return PanFormat.RULE_CODE;
    }

    @Override
    public List<ValidationFinding> execute(ValidationContext context, List<ParsedLedger> parsedLedgers) {
        List<ValidationFinding> findings = new ArrayList<>();
        for (ParsedLedger ledger : parsedLedgers) {
            if (ledger == null || ledger.getPan() == null || ledger.getPan().isBlank()) {
                continue;
            }
            if (PanFormat.isValid(ledger.getPan())) {
                continue;
            }
            if (panOverrides.findPan(context.organizationId(), ledger.getName()).isPresent()) {
                continue;
            }
            ValidationFinding f = new ValidationFinding();
            f.setRuleCode(getRuleCode());
            f.setLedgerName(ledger.getName());
            f.setCategory(ledger.getCategory());
            f.setSeverity(FindingSeverity.HIGH);
            f.setResolveStatus(ResolveStatus.OPEN);
            f.setActualValue(ledger.getPan().trim());
            f.setMessage("Ledger '" + ledger.getName() + "' has PAN '" + ledger.getPan().trim()
                    + "', which is not a valid 10-character PAN (format AAAAA9999A).");
            f.setSuggestedFix("Enter the 10-character PAN (e.g. AAPPK2262D) on this finding, "
                    + "or correct it in Tally and re-upload the masters.");
            findings.add(f);
        }
        return findings;
    }
}
