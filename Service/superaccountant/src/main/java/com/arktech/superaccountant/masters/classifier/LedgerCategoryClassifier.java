package com.arktech.superaccountant.masters.classifier;

import com.arktech.superaccountant.masters.models.LedgerCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Classifies ledger masters into categories by walking the group hierarchy tree.
 *
 * Root group names are case-insensitive exact matches against TallyPrime 7.0+ standard
 * chart of accounts. These names must be verified against a real Tally export before
 * modifying — incorrect names cause silent misclassification to OTHER.
 */
@Component
public class LedgerCategoryClassifier {

    private static final Logger logger = LoggerFactory.getLogger(LedgerCategoryClassifier.class);

    private static final Set<String> PURCHASE_GROUPS = Set.of("purchase accounts");
    private static final Set<String> EXPENSE_GROUPS = Set.of("direct expenses", "indirect expenses");
    private static final Set<String> INCOME_GROUPS = Set.of("direct incomes", "indirect incomes", "sales accounts");
    private static final Set<String> CREDITOR_GROUPS = Set.of("sundry creditors");
    private static final Set<String> DUTIES_AND_TAXES_GROUPS = Set.of("duties & taxes");

    public LedgerCategory classify(String parentGroup, Boolean gstApplicable, Boolean tdsApplicable,
                                   Map<String, String> groupHierarchy) {
        if (parentGroup == null || parentGroup.isBlank()) {
            return LedgerCategory.OTHER;
        }

        LedgerCategory result = classifyByAncestorWalk(parentGroup.toLowerCase(), gstApplicable, tdsApplicable,
                groupHierarchy);
        if (result != null) {
            return result;
        }

        return classifyByKeyword(parentGroup);
    }

    private LedgerCategory classifyByAncestorWalk(String startGroup, Boolean gstApplicable, Boolean tdsApplicable,
                                                   Map<String, String> hierarchy) {
        String current = startGroup;
        Set<String> visited = new HashSet<>();

        while (current != null && !current.isBlank()) {
            if (visited.contains(current)) {
                logger.warn("Cycle detected in group hierarchy at node: {}", current);
                return null;
            }
            visited.add(current);

            LedgerCategory match = matchGroup(current, gstApplicable, tdsApplicable);
            if (match != null) {
                return match;
            }

            String parentName = hierarchy.get(current);
            if (parentName == null || parentName.isBlank()) {
                break;
            }

            String parentKey = parentName.toLowerCase();
            if (!hierarchy.containsKey(parentKey)) {
                LedgerCategory parentMatch = matchGroup(parentKey, gstApplicable, tdsApplicable);
                if (parentMatch != null) return parentMatch;
                break;
            }

            current = parentKey;
        }

        return null;
    }

    private LedgerCategory matchGroup(String groupNameLower, Boolean gstApplicable, Boolean tdsApplicable) {
        if (PURCHASE_GROUPS.contains(groupNameLower)) return LedgerCategory.PURCHASE;
        if (EXPENSE_GROUPS.contains(groupNameLower)) return LedgerCategory.EXPENSE;
        if (INCOME_GROUPS.contains(groupNameLower)) return LedgerCategory.INCOME;
        if (CREDITOR_GROUPS.contains(groupNameLower)) return LedgerCategory.CREDITOR;
        if (DUTIES_AND_TAXES_GROUPS.contains(groupNameLower)) {
            if (Boolean.TRUE.equals(gstApplicable)) return LedgerCategory.GST;
            if (Boolean.TRUE.equals(tdsApplicable)) return LedgerCategory.TDS;
            return LedgerCategory.OTHER;
        }
        return null;
    }

    private LedgerCategory classifyByKeyword(String groupName) {
        if (groupName == null) return LedgerCategory.OTHER;
        String lower = groupName.toLowerCase();
        if (lower.contains("creditor")) return LedgerCategory.CREDITOR;
        if (lower.contains("purchase")) return LedgerCategory.PURCHASE;
        if (lower.contains("direct expense") || lower.contains("indirect expense")) return LedgerCategory.EXPENSE;
        if (lower.contains("income") || lower.contains("sales")) return LedgerCategory.INCOME;
        return LedgerCategory.OTHER;
    }
}
