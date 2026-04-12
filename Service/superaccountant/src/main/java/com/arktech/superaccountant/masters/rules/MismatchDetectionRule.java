package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.classifier.ParsedLedger;
import com.arktech.superaccountant.masters.models.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Compares uploaded ledgers against pre-configured masters.
 * Emits ALL applicable mismatches per ledger pair (not first-one-wins).
 */
@Component("MISMATCH_DETECTION")
public class MismatchDetectionRule implements ValidationRule {

    @Override
    public String getRuleCode() {
        return "MISMATCH_DETECTION";
    }

    @Override
    public List<ValidationFinding> execute(ValidationContext context, List<ParsedLedger> parsedLedgers) {
        List<ValidationFinding> findings = new ArrayList<>();

        // Only compare ledgers that have a known category (not OTHER)
        Map<String, ParsedLedger> uploadedMap = parsedLedgers.stream()
                .filter(l -> l.getCategory() != LedgerCategory.OTHER)
                .collect(Collectors.toMap(
                        l -> normalize(l.getName()),
                        l -> l,
                        (existing, replacement) -> existing // keep first on duplicate names
                ));

        Map<String, PreconfiguredMaster> configuredMap = context.preconfiguredMasters().stream()
                .collect(Collectors.toMap(
                        m -> normalize(m.getLedgerName()),
                        m -> m,
                        (existing, replacement) -> existing
                ));

        // 1. Configured masters missing from upload
        for (Map.Entry<String, PreconfiguredMaster> entry : configuredMap.entrySet()) {
            if (!uploadedMap.containsKey(entry.getKey())) {
                findings.add(buildFinding(entry.getValue().getLedgerName(),
                        entry.getValue().getCategory(),
                        MismatchType.MISSING_IN_UPLOAD,
                        FindingSeverity.WARNING,
                        entry.getValue().getLedgerName(), null, null));
            }
        }

        // 2. Uploaded ledgers not in configured masters
        for (Map.Entry<String, ParsedLedger> entry : uploadedMap.entrySet()) {
            if (!configuredMap.containsKey(entry.getKey())) {
                findings.add(buildFinding(entry.getValue().getName(),
                        entry.getValue().getCategory(),
                        MismatchType.MISSING_IN_CONFIGURATION,
                        FindingSeverity.WARNING,
                        null, entry.getValue().getName(), null));
            }
        }

        // 3. Matched pairs — check all fields
        for (Map.Entry<String, ParsedLedger> entry : uploadedMap.entrySet()) {
            String normalizedName = entry.getKey();
            if (!configuredMap.containsKey(normalizedName)) {
                continue;
            }

            ParsedLedger uploaded = entry.getValue();
            PreconfiguredMaster configured = configuredMap.get(normalizedName);

            // NAME_MISMATCH (casing/whitespace difference)
            if (!uploaded.getName().equals(configured.getLedgerName())) {
                String suggestedFix = String.format(
                        "In TallyPrime: Gateway of Tally → Accounts Info → Ledgers → Alter → %s → rename to '%s' → Accept.",
                        uploaded.getName(), configured.getLedgerName());
                findings.add(buildFindingWithFix(uploaded.getName(), uploaded.getCategory(),
                        MismatchType.NAME_MISMATCH, FindingSeverity.INFO,
                        configured.getLedgerName(), uploaded.getName(), suggestedFix));
            }

            // PARENT_GROUP_MISMATCH
            if (configured.getExpectedParentGroup() != null && uploaded.getParentGroup() != null
                    && !normalize(uploaded.getParentGroup()).equals(normalize(configured.getExpectedParentGroup()))) {
                String suggestedFix = String.format(
                        "In TallyPrime: Gateway of Tally → Accounts Info → Ledgers → Alter → %s → change 'Under' from '%s' to '%s' → Accept.",
                        uploaded.getName(), uploaded.getParentGroup(), configured.getExpectedParentGroup());
                findings.add(buildFindingWithFix(uploaded.getName(), uploaded.getCategory(),
                        MismatchType.PARENT_GROUP_MISMATCH, FindingSeverity.ERROR,
                        configured.getExpectedParentGroup(), uploaded.getParentGroup(), suggestedFix));
            }

            // GST_APPLICABILITY_MISMATCH
            if (configured.getExpectedGstApplicable() != null
                    && !Objects.equals(uploaded.getGstApplicable(), configured.getExpectedGstApplicable())) {
                findings.add(buildFinding(uploaded.getName(), uploaded.getCategory(),
                        MismatchType.GST_APPLICABILITY_MISMATCH, FindingSeverity.ERROR,
                        String.valueOf(configured.getExpectedGstApplicable()),
                        String.valueOf(uploaded.getGstApplicable()), null));
            }

            // TDS_APPLICABILITY_MISMATCH
            if (configured.getExpectedTdsApplicable() != null
                    && !Objects.equals(uploaded.getTdsApplicable(), configured.getExpectedTdsApplicable())) {
                findings.add(buildFinding(uploaded.getName(), uploaded.getCategory(),
                        MismatchType.TDS_APPLICABILITY_MISMATCH, FindingSeverity.ERROR,
                        String.valueOf(configured.getExpectedTdsApplicable()),
                        String.valueOf(uploaded.getTdsApplicable()), null));
            }
        }

        return findings;
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private ValidationFinding buildFinding(String ledgerName, LedgerCategory category,
                                            MismatchType type, FindingSeverity severity,
                                            String expected, String actual, String suggestedFix) {
        return buildFindingWithFix(ledgerName, category, type, severity, expected, actual, suggestedFix);
    }

    private ValidationFinding buildFindingWithFix(String ledgerName, LedgerCategory category,
                                                    MismatchType type, FindingSeverity severity,
                                                    String expected, String actual, String suggestedFix) {
        ValidationFinding f = new ValidationFinding();
        f.setRuleCode(getRuleCode());
        f.setLedgerName(ledgerName);
        f.setCategory(category);
        f.setMismatchType(type);
        f.setSeverity(severity);
        f.setExpectedValue(expected);
        f.setActualValue(actual);
        f.setSuggestedFix(suggestedFix);
        f.setResolveStatus(ResolveStatus.OPEN);
        f.setMessage(String.format("%s: Ledger '%s' — expected %s, found %s.",
                type.name(), ledgerName,
                expected != null ? expected : "present",
                actual != null ? actual : "absent"));
        return f;
    }
}
