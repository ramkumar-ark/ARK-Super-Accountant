package com.arktech.superaccountant.login.config;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.repository.PreconfiguredMasterRepository;
import com.arktech.superaccountant.masters.repository.ValidationRuleConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PreconfiguredMasterRepository preconfiguredMasterRepository;

    @Autowired
    ValidationRuleConfigRepository validationRuleConfigRepository;

    @Override
    public void run(String... args) throws Exception {
        seedRoles();
        seedValidationRules();
        seedConstructionTemplate();
    }

    private void seedRoles() {
        if (roleRepository.count() == 0) {
            roleRepository.save(new Role(ERole.ROLE_CASHIER));
            roleRepository.save(new Role(ERole.ROLE_ACCOUNTANT));
            roleRepository.save(new Role(ERole.ROLE_DATA_ENTRY_OPERATOR));
            roleRepository.save(new Role(ERole.ROLE_OWNER));
            System.out.println("Roles initialized.");
        }
    }

    private void seedValidationRules() {
        if (validationRuleConfigRepository.count() == 0) {
            ValidationRuleConfig rule = new ValidationRuleConfig();
            rule.setRuleCode("MISMATCH_DETECTION");
            rule.setRuleName("Mismatch Detection");
            rule.setDescription("Compares uploaded ledgers against pre-configured masters and emits mismatch findings.");
            rule.setActive(true);
            rule.setExecutionOrder(1);
            validationRuleConfigRepository.save(rule);
            System.out.println("Validation rules initialized.");
        }
    }

    private void seedConstructionTemplate() {
        // Template rows are org_id = null and is_template = true
        // They are copied per org at onboarding time
        long templateCount = preconfiguredMasterRepository.findByTemplateTrue().size();
        if (templateCount > 0) return;

        List<PreconfiguredMaster> templates = List.of(
            // ── PURCHASE ──────────────────────────────────────────────────────
            template("Cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null),
            template("TMT Steel Bars", LedgerCategory.PURCHASE, "Purchase Accounts", null, null),
            template("Sand and Aggregate", LedgerCategory.PURCHASE, "Purchase Accounts", null, null),
            template("Bitumen", LedgerCategory.PURCHASE, "Purchase Accounts", null, null),
            template("Construction Materials", LedgerCategory.PURCHASE, "Purchase Accounts", null, null),

            // ── EXPENSE (Direct) ───────────────────────────────────────────────
            template("Labour Charges", LedgerCategory.EXPENSE, "Direct Expenses", null, null),
            template("Sub-contractor Charges", LedgerCategory.EXPENSE, "Direct Expenses", null, true),
            template("Plant and Machinery Hire", LedgerCategory.EXPENSE, "Direct Expenses", null, null),
            template("Site Expenses", LedgerCategory.EXPENSE, "Direct Expenses", null, null),
            template("Diesel and Fuel", LedgerCategory.EXPENSE, "Direct Expenses", null, null),
            template("Royalty Charges", LedgerCategory.EXPENSE, "Direct Expenses", null, null),
            template("Excavation Charges", LedgerCategory.EXPENSE, "Direct Expenses", null, null),

            // ── EXPENSE (Indirect) ─────────────────────────────────────────────
            template("Office Expenses", LedgerCategory.EXPENSE, "Indirect Expenses", null, null),
            template("Administrative Charges", LedgerCategory.EXPENSE, "Indirect Expenses", null, null),
            template("Audit Fees", LedgerCategory.EXPENSE, "Indirect Expenses", null, null),

            // ── INCOME ────────────────────────────────────────────────────────
            template("Contract Receipts", LedgerCategory.INCOME, "Direct Incomes", null, null),
            template("RA Bill Receipts", LedgerCategory.INCOME, "Direct Incomes", null, null),
            template("Storm Water Drain Works", LedgerCategory.INCOME, "Direct Incomes", null, null),
            template("Road Works - Laying", LedgerCategory.INCOME, "Direct Incomes", null, null),
            template("Road Works - Relaying", LedgerCategory.INCOME, "Direct Incomes", null, null),

            // ── GST ───────────────────────────────────────────────────────────
            template("Input CGST @6%", LedgerCategory.GST, "Duties & Taxes", true, null),
            template("Input SGST @6%", LedgerCategory.GST, "Duties & Taxes", true, null),
            template("Input IGST @12%", LedgerCategory.GST, "Duties & Taxes", true, null),
            template("Output CGST @6%", LedgerCategory.GST, "Duties & Taxes", true, null),
            template("Output SGST @6%", LedgerCategory.GST, "Duties & Taxes", true, null),

            // ── TDS ───────────────────────────────────────────────────────────
            template("TDS Payable - 194C Contractor", LedgerCategory.TDS, "Duties & Taxes", null, true),
            template("TDS Receivable - 194C", LedgerCategory.TDS, "Duties & Taxes", null, true)
        );

        preconfiguredMasterRepository.saveAll(templates);
        System.out.println("Construction/Works Contractor template seeded (" + templates.size() + " masters).");
    }

    private PreconfiguredMaster template(String name, LedgerCategory category,
                                          String parentGroup, Boolean gstApplicable, Boolean tdsApplicable) {
        PreconfiguredMaster m = new PreconfiguredMaster();
        m.setOrganizationId(null); // template rows have no org
        m.setLedgerName(name);
        m.setCategory(category);
        m.setExpectedParentGroup(parentGroup);
        m.setExpectedGstApplicable(gstApplicable);
        m.setExpectedTdsApplicable(tdsApplicable);
        m.setTemplate(true);
        m.setActive(true);
        return m;
    }
}
