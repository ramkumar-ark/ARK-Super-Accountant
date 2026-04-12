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

    // TallyPrime 7.0+ standard root group names (case-insensitive)
    private static final Set<String> PURCHASE_ROOTS = Set.of("purchase accounts");
    private static final Set<String> EXPENSE_ROOTS = Set.of("direct expenses", "indirect expenses");
    private static final Set<String> INCOME_ROOTS = Set.of("direct incomes", "indirect incomes", "sales accounts");
    private static final String DUTIES_AND_TAXES = "duties & taxes";

    /**
     * Classify a single ledger into a category.
     *
     * @param parentGroup  the direct parent group name of the ledger
     * @param gstApplicable whether the ledger has GST tax type
     * @param tdsApplicable whether the ledger has TDS applicable flag
     * @param groupHierarchy map of groupName -> parentGroupName from the uploaded JSON
     * @return the resolved LedgerCategory
     */
    public LedgerCategory classify(String parentGroup, Boolean gstApplicable, Boolean tdsApplicable,
                                   Map<String, String> groupHierarchy) {
        if (parentGroup == null || parentGroup.isBlank()) {
            return LedgerCategory.OTHER;
        }

        String rootGroup = findRootGroup(parentGroup, groupHierarchy, new HashSet<>());
        if (rootGroup == null) {
            return LedgerCategory.OTHER;
        }

        String rootLower = rootGroup.toLowerCase();

        if (PURCHASE_ROOTS.contains(rootLower)) {
            return LedgerCategory.PURCHASE;
        }
        if (EXPENSE_ROOTS.contains(rootLower)) {
            return LedgerCategory.EXPENSE;
        }
        if (INCOME_ROOTS.contains(rootLower)) {
            return LedgerCategory.INCOME;
        }
        if (DUTIES_AND_TAXES.equals(rootLower)) {
            // Distinguish GST vs TDS within Duties & Taxes
            if (Boolean.TRUE.equals(gstApplicable)) {
                return LedgerCategory.GST;
            }
            if (Boolean.TRUE.equals(tdsApplicable)) {
                return LedgerCategory.TDS;
            }
            // Duties & Taxes with neither flag → OTHER (catch-all)
            return LedgerCategory.OTHER;
        }

        return LedgerCategory.OTHER;
    }

    /**
     * Walk up the group hierarchy until we find a root group (one not present as a key
     * in the hierarchy map, meaning it has no parent listed in the file).
     *
     * Returns null if a cycle is detected or the starting group is not in the map at all.
     */
    private String findRootGroup(String groupName, Map<String, String> hierarchy, Set<String> visited) {
        if (groupName == null || groupName.isBlank()) {
            return null;
        }

        // If we've seen this node before, it's a cycle
        if (visited.contains(groupName.toLowerCase())) {
            logger.warn("Cycle detected in group hierarchy at node: {}", groupName);
            return null;
        }
        visited.add(groupName.toLowerCase());

        String parentName = hierarchy.get(groupName);
        if (parentName == null || parentName.isBlank()) {
            // This group has no parent in the file — it IS a root group
            return groupName;
        }

        // Check if the parent itself exists in the hierarchy map
        // (parent could be a standard Tally built-in not listed as a group object)
        if (!hierarchy.containsKey(parentName)) {
            // Parent is not in the file's group map — treat parentName as the effective root
            return parentName;
        }

        return findRootGroup(parentName, hierarchy, visited);
    }
}
