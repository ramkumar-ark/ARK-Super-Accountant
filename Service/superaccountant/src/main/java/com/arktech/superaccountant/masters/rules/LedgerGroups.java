package com.arktech.superaccountant.masters.rules;

import com.arktech.superaccountant.masters.models.PreconfiguredMaster;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Group-membership checks driven by the ledger's parent-group chain from the uploaded Tally data, so
 * user-created sub-groups of a standard group count as members. When a master has no stored chain (created
 * before chains were recorded), its expected parent group stands in as a one-group chain.
 */
final class LedgerGroups {

    static final Set<String> EXPENSE = Set.of("direct expenses", "indirect expenses");
    static final Set<String> INCOME = Set.of("direct incomes", "indirect incomes", "sales accounts");
    static final Set<String> FIXED_ASSETS = Set.of("fixed assets");
    static final Set<String> GSTIN_PARTY_AND_LOAN_GROUPS = Set.of(
            "sundry creditors", "sundry debtors", "loans & advances (asset)", "loans & advances",
            "secured loans", "unsecured loans");

    private LedgerGroups() {}

    static List<String> chain(PreconfiguredMaster m) {
        String chain = m.getGroupChain();
        if (chain != null && !chain.isBlank()) {
            return Arrays.stream(chain.split("\\|")).map(s -> s.trim().toLowerCase()).toList();
        }
        String parent = m.getExpectedParentGroup();
        return parent == null || parent.isBlank() ? List.of() : List.of(parent.trim().toLowerCase());
    }

    static boolean inAny(PreconfiguredMaster m, Set<String> groups) {
        return chain(m).stream().anyMatch(groups::contains);
    }
}
