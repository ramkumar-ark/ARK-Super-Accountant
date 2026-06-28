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

import java.util.ArrayList;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PreconfiguredMasterRepository preconfiguredMasterRepository;

    @Autowired
    ValidationRuleConfigRepository validationRuleConfigRepository;

    @Autowired
    DataMigrationService dataMigrationService;

    @Override
    public void run(String... args) throws Exception {
        seedRoles();
        seedValidationRuleIfAbsent("MISMATCH_DETECTION", "Mismatch Detection",
                "Compares uploaded ledgers against pre-configured masters for category mismatches.", 1);
        seedValidationRuleIfAbsent("TDS_SECTION_MAPPING", "TDS Section Mapping",
                "Flags ledgers with no TDS section assigned.", 2);
        seedValidationRuleIfAbsent("GST_APPLICABILITY", "GST Applicability",
                "Flags income/GST ledgers with no GST applicability type.", 3);
        seedValidationRuleIfAbsent("HSN_SAC_CODE", "HSN/SAC Code Presence",
                "Flags taxable income/GST ledgers with no HSN or SAC code.", 4);
        seedValidationRuleIfAbsent("GSTIN_PRESENCE", "GSTIN Presence",
                "Flags purchase ledgers with no GSTIN for GSTR-2B reconciliation.", 5);
        seedConstructionTemplate();
        if (preconfiguredMasterRepository.findByTemplateTrueAndTemplateSlug("standard").isEmpty()) {
            seedStandardTemplate();
        }
        if (preconfiguredMasterRepository.findByTemplateTrueAndTemplateSlug("simplified").isEmpty()) {
            seedSimplifiedTemplate();
        }
        if (preconfiguredMasterRepository.findByTemplateTrueAndTemplateSlug("manufacturing").isEmpty()) {
            seedManufacturingTemplate();
        }
        int backfilled = dataMigrationService.backfillFindingSeverities();
        if (backfilled > 0) System.out.println("Backfilled " + backfilled + " finding severity values to HIGH/MEDIUM/LOW.");
    }

    private void seedRoles() {
        int added = 0;
        for (ERole eRole : ERole.values()) {
            if (roleRepository.findByName(eRole).isEmpty()) {
                roleRepository.save(new Role(eRole));
                added++;
            }
        }
        if (added > 0) {
            System.out.println("Roles initialized/updated (" + added + " new).");
        }
    }

    private void seedValidationRuleIfAbsent(String code, String name, String description, int order) {
        if (!validationRuleConfigRepository.existsByRuleCode(code)) {
            ValidationRuleConfig rule = new ValidationRuleConfig();
            rule.setRuleCode(code);
            rule.setRuleName(name);
            rule.setDescription(description);
            rule.setActive(true);
            rule.setExecutionOrder(order);
            validationRuleConfigRepository.save(rule);
            System.out.println("Validation rule seeded: " + code);
        }
    }

    private static final String CONSTRUCTION_SLUG = "construction";

    private void seedConstructionTemplate() {
        // Template rows are org_id = null and is_template = true
        // They are copied per org at onboarding time
        // Guard is slug-specific so it matches the pattern used for all other named templates
        if (!preconfiguredMasterRepository
                .findByTemplateTrueAndTemplateSlug(CONSTRUCTION_SLUG).isEmpty()) return;

        List<PreconfiguredMaster> templates = List.of(
            // ── PURCHASE ──────────────────────────────────────────────────────
            template("Cement", LedgerCategory.PURCHASE, "Purchase Accounts", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("TMT Steel Bars", LedgerCategory.PURCHASE, "Purchase Accounts", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("Sand and Aggregate", LedgerCategory.PURCHASE, "Purchase Accounts", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("Bitumen", LedgerCategory.PURCHASE, "Purchase Accounts", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("Construction Materials", LedgerCategory.PURCHASE, "Purchase Accounts", null, null, CONSTRUCTION_SLUG, null, null, null, null),

            // ── EXPENSE (Direct) ───────────────────────────────────────────────
            template("Labour Charges", LedgerCategory.EXPENSE, "Direct Expenses", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("Sub-contractor Charges", LedgerCategory.EXPENSE, "Direct Expenses", null, true, CONSTRUCTION_SLUG, "194C", null, null, null),
            template("Plant and Machinery Hire", LedgerCategory.EXPENSE, "Direct Expenses", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("Site Expenses", LedgerCategory.EXPENSE, "Direct Expenses", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("Diesel and Fuel", LedgerCategory.EXPENSE, "Direct Expenses", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("Royalty Charges", LedgerCategory.EXPENSE, "Direct Expenses", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("Excavation Charges", LedgerCategory.EXPENSE, "Direct Expenses", null, null, CONSTRUCTION_SLUG, null, null, null, null),

            // ── EXPENSE (Indirect) ─────────────────────────────────────────────
            template("Office Expenses", LedgerCategory.EXPENSE, "Indirect Expenses", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("Administrative Charges", LedgerCategory.EXPENSE, "Indirect Expenses", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("Audit Fees", LedgerCategory.EXPENSE, "Indirect Expenses", null, null, CONSTRUCTION_SLUG, null, null, null, null),

            // ── INCOME ────────────────────────────────────────────────────────
            template("Contract Receipts", LedgerCategory.INCOME, "Direct Incomes", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("RA Bill Receipts", LedgerCategory.INCOME, "Direct Incomes", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("Storm Water Drain Works", LedgerCategory.INCOME, "Direct Incomes", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("Road Works - Laying", LedgerCategory.INCOME, "Direct Incomes", null, null, CONSTRUCTION_SLUG, null, null, null, null),
            template("Road Works - Relaying", LedgerCategory.INCOME, "Direct Incomes", null, null, CONSTRUCTION_SLUG, null, null, null, null),

            // ── GST ───────────────────────────────────────────────────────────
            template("Input CGST @6%", LedgerCategory.GST, "Duties & Taxes", true, null, CONSTRUCTION_SLUG, "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null),
            template("Input SGST @6%", LedgerCategory.GST, "Duties & Taxes", true, null, CONSTRUCTION_SLUG, "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null),
            template("Input IGST @12%", LedgerCategory.GST, "Duties & Taxes", true, null, CONSTRUCTION_SLUG, "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null),
            template("Output CGST @6%", LedgerCategory.GST, "Duties & Taxes", true, null, CONSTRUCTION_SLUG, "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null),
            template("Output SGST @6%", LedgerCategory.GST, "Duties & Taxes", true, null, CONSTRUCTION_SLUG, "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null),

            // ── TDS ───────────────────────────────────────────────────────────
            template("TDS Payable - 194C Contractor", LedgerCategory.TDS, "Duties & Taxes", null, true, CONSTRUCTION_SLUG, "194C", GstApplicabilityType.NOT_APPLICABLE, null, null),
            template("TDS Receivable - 194C", LedgerCategory.TDS, "Duties & Taxes", null, true, CONSTRUCTION_SLUG, "194C", GstApplicabilityType.NOT_APPLICABLE, null, null)
        );

        preconfiguredMasterRepository.saveAll(templates);
        System.out.println("Construction/Works Contractor template seeded (" + templates.size() + " masters).");
    }

    private void seedStandardTemplate() {
        List<PreconfiguredMaster> rows = new ArrayList<>();
        // Capital Accounts
        rows.add(template("Share Capital", LedgerCategory.OTHER, "Capital Account", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Reserves & Surplus", LedgerCategory.OTHER, "Reserves & Surplus", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Capital Account", LedgerCategory.OTHER, "Capital Account", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // Current Liabilities
        rows.add(template("Sundry Creditors", LedgerCategory.PURCHASE, "Sundry Creditors", true, true, "standard", "194Q", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Output IGST", LedgerCategory.GST, "Duties & Taxes", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9954", null));
        rows.add(template("Output CGST", LedgerCategory.GST, "Duties & Taxes", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9954", null));
        rows.add(template("Output SGST", LedgerCategory.GST, "Duties & Taxes", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9954", null));
        rows.add(template("TDS Payable", LedgerCategory.TDS, "Duties & Taxes", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Salary Payable", LedgerCategory.EXPENSE, "Current Liabilities", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Advance from Customers", LedgerCategory.OTHER, "Current Liabilities", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // Fixed Assets
        rows.add(template("Plant & Machinery", LedgerCategory.EXPENSE, "Fixed Assets", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Furniture & Fittings", LedgerCategory.EXPENSE, "Fixed Assets", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Land & Building", LedgerCategory.EXPENSE, "Fixed Assets", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Computer & Peripherals", LedgerCategory.EXPENSE, "Fixed Assets", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Vehicles", LedgerCategory.EXPENSE, "Fixed Assets", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // Current Assets
        rows.add(template("Cash", LedgerCategory.OTHER, "Cash-in-Hand", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Bank Account", LedgerCategory.OTHER, "Bank Accounts", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Bank OD Account", LedgerCategory.OTHER, "Bank OD A/C", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Sundry Debtors", LedgerCategory.INCOME, "Sundry Debtors", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Stock-in-Hand", LedgerCategory.PURCHASE, "Stock-in-Hand", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Advance to Suppliers", LedgerCategory.PURCHASE, "Current Assets", true, true, "standard", "194Q", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Prepaid Expenses", LedgerCategory.EXPENSE, "Current Assets", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Input IGST", LedgerCategory.GST, "Current Assets", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Input CGST", LedgerCategory.GST, "Current Assets", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Input SGST", LedgerCategory.GST, "Current Assets", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("TDS Receivable", LedgerCategory.TDS, "Current Assets", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // Income
        rows.add(template("Sales (Domestic)", LedgerCategory.INCOME, "Sales Accounts", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9999", null));
        rows.add(template("Sales (Export)", LedgerCategory.INCOME, "Sales Accounts", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.ZERO_RATED, "9999", null));
        rows.add(template("Other Income", LedgerCategory.INCOME, "Indirect Incomes", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9997", null));
        rows.add(template("Interest Received", LedgerCategory.INCOME, "Indirect Incomes", false, true, "standard", "194A", GstApplicabilityType.EXEMPT, null, null));
        rows.add(template("Commission Received", LedgerCategory.INCOME, "Indirect Incomes", true, true, "standard", "194H", GstApplicabilityType.TAXABLE, "997113", null));
        rows.add(template("Discount Received", LedgerCategory.INCOME, "Indirect Incomes", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // Purchase and Direct Expenses
        rows.add(template("Purchase (Domestic)", LedgerCategory.PURCHASE, "Purchase Accounts", true, true, "standard", "194Q", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Purchase (Import)", LedgerCategory.PURCHASE, "Purchase Accounts", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Purchase Returns", LedgerCategory.PURCHASE, "Purchase Accounts", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Freight & Cartage", LedgerCategory.EXPENSE, "Direct Expenses", true, true, "standard", "194C", GstApplicabilityType.TAXABLE, "996812", null));
        // Indirect Expenses
        rows.add(template("Rent", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "standard", "194I", GstApplicabilityType.TAXABLE, "997212", null));
        rows.add(template("Professional Fees", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "standard", "194J_B", GstApplicabilityType.TAXABLE, "998311", null));
        rows.add(template("Technical Services", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "standard", "194J_A", GstApplicabilityType.TAXABLE, "998312", null));
        rows.add(template("Commission Paid", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "standard", "194H", GstApplicabilityType.TAXABLE, "997113", null));
        rows.add(template("Repairs & Maintenance", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "standard", "194C", GstApplicabilityType.TAXABLE, "998719", null));
        rows.add(template("Power & Fuel", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "270119", null));
        rows.add(template("Insurance", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "997132", null));
        rows.add(template("Salary & Wages", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Advertisement", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "standard", "194C", GstApplicabilityType.TAXABLE, "998361", null));
        rows.add(template("Travelling Expenses", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "996311", null));
        rows.add(template("Printing & Stationery", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "998414", null));
        rows.add(template("Telephone & Internet", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "998421", null));
        rows.add(template("Bank Charges", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.EXEMPT, null, null));
        rows.add(template("Staff Welfare", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NON_GST, null, null));
        rows.add(template("Vehicle Running", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "standard", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "996791", null));
        rows.add(template("Audit Fees", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "standard", "194J_B", GstApplicabilityType.TAXABLE, "998311", null));
        rows.add(template("Legal Fees", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "standard", "194J_B", GstApplicabilityType.TAXABLE, "998311", null));
        rows.add(template("Security Charges", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "standard", "194C", GstApplicabilityType.TAXABLE, "998524", null));
        rows.add(template("Contract Labour", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "standard", "194C", GstApplicabilityType.TAXABLE, "998519", null));
        rows.add(template("Housekeeping", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "standard", "194C", GstApplicabilityType.TAXABLE, "998534", null));
        rows.add(template("Discount Allowed", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Bad Debts", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Depreciation", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // TDS Payable accounts
        rows.add(template("TDS Payable (194C)", LedgerCategory.TDS, "Duties & Taxes", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("TDS Payable (194J)", LedgerCategory.TDS, "Duties & Taxes", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("TDS Payable (194H)", LedgerCategory.TDS, "Duties & Taxes", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("TDS Payable (194I)", LedgerCategory.TDS, "Duties & Taxes", false, false, "standard", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        preconfiguredMasterRepository.saveAll(rows);
        System.out.println("Standard template seeded (" + rows.size() + " masters).");
    }

    private void seedSimplifiedTemplate() {
        List<PreconfiguredMaster> rows = new ArrayList<>();
        // Capital
        rows.add(template("Capital Account", LedgerCategory.OTHER, "Capital Account", false, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // Current Liabilities
        rows.add(template("Sundry Creditors", LedgerCategory.PURCHASE, "Sundry Creditors", true, true, "simplified", "194Q", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Output IGST", LedgerCategory.GST, "Duties & Taxes", true, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9954", null));
        rows.add(template("Output CGST", LedgerCategory.GST, "Duties & Taxes", true, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9954", null));
        rows.add(template("Output SGST", LedgerCategory.GST, "Duties & Taxes", true, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9954", null));
        rows.add(template("TDS Payable", LedgerCategory.TDS, "Duties & Taxes", false, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // Current Assets
        rows.add(template("Cash", LedgerCategory.OTHER, "Cash-in-Hand", false, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Bank Account", LedgerCategory.OTHER, "Bank Accounts", false, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Sundry Debtors", LedgerCategory.INCOME, "Sundry Debtors", true, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Stock-in-Hand", LedgerCategory.PURCHASE, "Stock-in-Hand", false, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Advance to Suppliers", LedgerCategory.PURCHASE, "Current Assets", true, true, "simplified", "194Q", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Input IGST", LedgerCategory.GST, "Current Assets", true, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Input CGST", LedgerCategory.GST, "Current Assets", true, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Input SGST", LedgerCategory.GST, "Current Assets", true, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("TDS Receivable", LedgerCategory.TDS, "Current Assets", false, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // Income
        rows.add(template("Sales (Domestic)", LedgerCategory.INCOME, "Sales Accounts", true, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9999", null));
        rows.add(template("Other Income", LedgerCategory.INCOME, "Indirect Incomes", true, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9997", null));
        rows.add(template("Interest Received", LedgerCategory.INCOME, "Indirect Incomes", false, true, "simplified", "194A", GstApplicabilityType.EXEMPT, null, null));
        // Purchase
        rows.add(template("Purchase (Domestic)", LedgerCategory.PURCHASE, "Purchase Accounts", true, true, "simplified", "194Q", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Freight & Cartage", LedgerCategory.EXPENSE, "Direct Expenses", true, true, "simplified", "194C", GstApplicabilityType.TAXABLE, "996812", null));
        // Expenses
        rows.add(template("Rent", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "simplified", "194I", GstApplicabilityType.TAXABLE, "997212", null));
        rows.add(template("Professional Fees", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "simplified", "194J_B", GstApplicabilityType.TAXABLE, "998311", null));
        rows.add(template("Repairs & Maintenance", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "simplified", "194C", GstApplicabilityType.TAXABLE, "998719", null));
        rows.add(template("Advertisement", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "simplified", "194C", GstApplicabilityType.TAXABLE, "998361", null));
        rows.add(template("Printing & Stationery", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "998414", null));
        rows.add(template("Telephone & Internet", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "998421", null));
        rows.add(template("Salary & Wages", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Bank Charges", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.EXEMPT, null, null));
        rows.add(template("Travelling Expenses", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "996311", null));
        rows.add(template("Insurance", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "997132", null));
        rows.add(template("Audit Fees", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "simplified", "194J_B", GstApplicabilityType.TAXABLE, "998311", null));
        rows.add(template("Discount Allowed", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "simplified", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        preconfiguredMasterRepository.saveAll(rows);
        System.out.println("Simplified template seeded (" + rows.size() + " masters).");
    }

    private void seedManufacturingTemplate() {
        List<PreconfiguredMaster> rows = new ArrayList<>();
        // Capital Accounts
        rows.add(template("Share Capital", LedgerCategory.OTHER, "Capital Account", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Reserves & Surplus", LedgerCategory.OTHER, "Reserves & Surplus", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Capital Account", LedgerCategory.OTHER, "Capital Account", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // Current Liabilities
        rows.add(template("Sundry Creditors", LedgerCategory.PURCHASE, "Sundry Creditors", true, true, "manufacturing", "194Q", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Output IGST", LedgerCategory.GST, "Duties & Taxes", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9954", null));
        rows.add(template("Output CGST", LedgerCategory.GST, "Duties & Taxes", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9954", null));
        rows.add(template("Output SGST", LedgerCategory.GST, "Duties & Taxes", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9954", null));
        rows.add(template("TDS Payable", LedgerCategory.TDS, "Duties & Taxes", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Salary Payable", LedgerCategory.EXPENSE, "Current Liabilities", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Advance from Customers", LedgerCategory.OTHER, "Current Liabilities", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // Fixed Assets
        rows.add(template("Plant & Machinery", LedgerCategory.EXPENSE, "Fixed Assets", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Furniture & Fittings", LedgerCategory.EXPENSE, "Fixed Assets", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Land & Building", LedgerCategory.EXPENSE, "Fixed Assets", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Computer & Peripherals", LedgerCategory.EXPENSE, "Fixed Assets", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Vehicles", LedgerCategory.EXPENSE, "Fixed Assets", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Machinery & Equipment", LedgerCategory.EXPENSE, "Fixed Assets", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Factory Building", LedgerCategory.EXPENSE, "Fixed Assets", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // Current Assets
        rows.add(template("Cash", LedgerCategory.OTHER, "Cash-in-Hand", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Bank Account", LedgerCategory.OTHER, "Bank Accounts", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Bank OD Account", LedgerCategory.OTHER, "Bank OD A/C", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Sundry Debtors", LedgerCategory.INCOME, "Sundry Debtors", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Stock-in-Hand", LedgerCategory.PURCHASE, "Stock-in-Hand", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Raw Material Stock", LedgerCategory.PURCHASE, "Stock-in-Hand", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Work-in-Progress", LedgerCategory.PURCHASE, "Stock-in-Hand", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Finished Goods", LedgerCategory.PURCHASE, "Stock-in-Hand", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Advance to Suppliers", LedgerCategory.PURCHASE, "Current Assets", true, true, "manufacturing", "194Q", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Prepaid Expenses", LedgerCategory.EXPENSE, "Current Assets", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Input IGST", LedgerCategory.GST, "Current Assets", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Input CGST", LedgerCategory.GST, "Current Assets", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Input SGST", LedgerCategory.GST, "Current Assets", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("TDS Receivable", LedgerCategory.TDS, "Current Assets", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // Income
        rows.add(template("Sales (Domestic)", LedgerCategory.INCOME, "Sales Accounts", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9999", null));
        rows.add(template("Sales (Export)", LedgerCategory.INCOME, "Sales Accounts", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.ZERO_RATED, "9999", null));
        rows.add(template("Sales (Finished Goods)", LedgerCategory.INCOME, "Sales Accounts", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9999", null));
        rows.add(template("Sales (Scrap)", LedgerCategory.INCOME, "Sales Accounts", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9965", null));
        rows.add(template("Job Work Income", LedgerCategory.INCOME, "Sales Accounts", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "998898", null));
        rows.add(template("Other Income", LedgerCategory.INCOME, "Indirect Incomes", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "9997", null));
        rows.add(template("Interest Received", LedgerCategory.INCOME, "Indirect Incomes", false, true, "manufacturing", "194A", GstApplicabilityType.EXEMPT, null, null));
        rows.add(template("Commission Received", LedgerCategory.INCOME, "Indirect Incomes", true, true, "manufacturing", "194H", GstApplicabilityType.TAXABLE, "997113", null));
        rows.add(template("Discount Received", LedgerCategory.INCOME, "Indirect Incomes", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // Purchase and Direct Expenses
        rows.add(template("Purchase (Domestic)", LedgerCategory.PURCHASE, "Purchase Accounts", true, true, "manufacturing", "194Q", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Purchase (Import)", LedgerCategory.PURCHASE, "Purchase Accounts", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Purchase Returns", LedgerCategory.PURCHASE, "Purchase Accounts", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Raw Material Purchase", LedgerCategory.PURCHASE, "Purchase Accounts", true, true, "manufacturing", "194Q", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Packing Material", LedgerCategory.PURCHASE, "Purchase Accounts", true, true, "manufacturing", "194Q", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Consumables", LedgerCategory.PURCHASE, "Purchase Accounts", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, null, null));
        rows.add(template("Freight & Cartage", LedgerCategory.EXPENSE, "Direct Expenses", true, true, "manufacturing", "194C", GstApplicabilityType.TAXABLE, "996812", null));
        // Manufacturing Expenses
        rows.add(template("Factory Power & Fuel", LedgerCategory.EXPENSE, "Manufacturing Expenses", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "270119", null));
        rows.add(template("Factory Rent", LedgerCategory.EXPENSE, "Manufacturing Expenses", true, true, "manufacturing", "194I", GstApplicabilityType.TAXABLE, "997212", null));
        rows.add(template("Machine Repairs", LedgerCategory.EXPENSE, "Manufacturing Expenses", true, true, "manufacturing", "194C", GstApplicabilityType.TAXABLE, "998719", null));
        rows.add(template("Quality Testing", LedgerCategory.EXPENSE, "Manufacturing Expenses", true, true, "manufacturing", "194J_A", GstApplicabilityType.TAXABLE, "998314", null));
        rows.add(template("Tooling & Dies", LedgerCategory.EXPENSE, "Manufacturing Expenses", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "998719", null));
        rows.add(template("Job Work Expenses", LedgerCategory.EXPENSE, "Manufacturing Expenses", true, true, "manufacturing", "194C", GstApplicabilityType.TAXABLE, "998898", null));
        rows.add(template("Labour (Manufacturing)", LedgerCategory.EXPENSE, "Manufacturing Expenses", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Effluent Treatment", LedgerCategory.EXPENSE, "Manufacturing Expenses", true, true, "manufacturing", "194C", GstApplicabilityType.TAXABLE, "999000", null));
        rows.add(template("Plant Maintenance", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "manufacturing", "194C", GstApplicabilityType.TAXABLE, "998719", null));
        // Indirect Expenses
        rows.add(template("Rent", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "manufacturing", "194I", GstApplicabilityType.TAXABLE, "997212", null));
        rows.add(template("Professional Fees", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "manufacturing", "194J_B", GstApplicabilityType.TAXABLE, "998311", null));
        rows.add(template("Technical Services", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "manufacturing", "194J_A", GstApplicabilityType.TAXABLE, "998312", null));
        rows.add(template("Commission Paid", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "manufacturing", "194H", GstApplicabilityType.TAXABLE, "997113", null));
        rows.add(template("Repairs & Maintenance", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "manufacturing", "194C", GstApplicabilityType.TAXABLE, "998719", null));
        rows.add(template("Power & Fuel", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "270119", null));
        rows.add(template("Insurance", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "997132", null));
        rows.add(template("Salary & Wages", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Advertisement", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "manufacturing", "194C", GstApplicabilityType.TAXABLE, "998361", null));
        rows.add(template("Travelling Expenses", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "996311", null));
        rows.add(template("Printing & Stationery", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "998414", null));
        rows.add(template("Telephone & Internet", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "998421", null));
        rows.add(template("Bank Charges", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.EXEMPT, null, null));
        rows.add(template("Staff Welfare", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NON_GST, null, null));
        rows.add(template("Vehicle Running", LedgerCategory.EXPENSE, "Indirect Expenses", true, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.TAXABLE, "996791", null));
        rows.add(template("Audit Fees", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "manufacturing", "194J_B", GstApplicabilityType.TAXABLE, "998311", null));
        rows.add(template("Legal Fees", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "manufacturing", "194J_B", GstApplicabilityType.TAXABLE, "998311", null));
        rows.add(template("Security Charges", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "manufacturing", "194C", GstApplicabilityType.TAXABLE, "998524", null));
        rows.add(template("Contract Labour", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "manufacturing", "194C", GstApplicabilityType.TAXABLE, "998519", null));
        rows.add(template("Housekeeping", LedgerCategory.EXPENSE, "Indirect Expenses", true, true, "manufacturing", "194C", GstApplicabilityType.TAXABLE, "998534", null));
        rows.add(template("Discount Allowed", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Bad Debts", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("Depreciation", LedgerCategory.EXPENSE, "Indirect Expenses", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        // TDS Payable accounts
        rows.add(template("TDS Payable (194C)", LedgerCategory.TDS, "Duties & Taxes", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("TDS Payable (194J)", LedgerCategory.TDS, "Duties & Taxes", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("TDS Payable (194H)", LedgerCategory.TDS, "Duties & Taxes", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        rows.add(template("TDS Payable (194I)", LedgerCategory.TDS, "Duties & Taxes", false, false, "manufacturing", "NOT_SUBJECT", GstApplicabilityType.NOT_APPLICABLE, null, null));
        preconfiguredMasterRepository.saveAll(rows);
        System.out.println("Manufacturing template seeded (" + rows.size() + " masters).");
    }

    // Legacy helper — used by construction template (no TDS/GST metadata)
    private PreconfiguredMaster template(String name, LedgerCategory category,
                                          String parentGroup, Boolean gstApplicable, Boolean tdsApplicable) {
        PreconfiguredMaster m = new PreconfiguredMaster();
        m.setOrganizationId(null);
        m.setLedgerName(name);
        m.setCategory(category);
        m.setExpectedParentGroup(parentGroup);
        m.setExpectedGstApplicable(gstApplicable);
        m.setExpectedTdsApplicable(tdsApplicable);
        m.setTemplate(true);
        m.setActive(true);
        return m;
    }

    // Extended helper — used by named templates with full TDS/GST metadata
    private PreconfiguredMaster template(String name, LedgerCategory category,
                                          String parentGroup, Boolean gstApplicable, Boolean tdsApplicable,
                                          String templateSlug, String tdsSection,
                                          GstApplicabilityType gstType, String hsnSacCode, String gstin) {
        PreconfiguredMaster m = new PreconfiguredMaster();
        m.setOrganizationId(null);
        m.setLedgerName(name);
        m.setCategory(category);
        m.setExpectedParentGroup(parentGroup);
        m.setExpectedGstApplicable(gstApplicable);
        m.setExpectedTdsApplicable(tdsApplicable);
        m.setTemplateSlug(templateSlug);
        m.setTdsSection(tdsSection);
        m.setGstApplicabilityType(gstType);
        m.setHsnSacCode(hsnSacCode);
        m.setGstin(gstin);
        m.setTemplate(true);
        m.setActive(true);
        return m;
    }
}
