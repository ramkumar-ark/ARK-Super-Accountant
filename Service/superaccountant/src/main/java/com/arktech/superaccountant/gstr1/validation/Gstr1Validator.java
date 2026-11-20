package com.arktech.superaccountant.gstr1.validation;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gstr1.models.Gstr1FillIn;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInScope;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.repository.Gstr1FillInRepository;
import com.arktech.superaccountant.gstr1.services.Gstr1Rules;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.FilerProfile;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.FilerProfileRepository;
import com.arktech.superaccountant.workflow.snapshot.VoucherKeys;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Prepared-return validator (plan Task T14). Runs entirely against the built
 * PreparedReturnRow payloads plus the vouchers behind them; it never mutates
 * rows. ERROR findings block Finalize (Gstr1FinalizePrecondition), WARNING
 * findings are advisory.
 */
@Component
public class Gstr1Validator {

    private static final String RETURN_KIND = "GSTR1";
    private static final BigDecimal TAX_TOLERANCE = BigDecimal.ONE;
    private static final BigDecimal IFF_CAP = new BigDecimal("5000000");
    private static final String INVOICE_NO_PATTERN = "^[A-Z0-9/-]{1,16}$";
    private static final String GSTIN_PATTERN = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]$";
    private static final String GSTIN_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Set<String> POS_REQUIRED =
            Set.of("B2B", "B2CL", "B2CS", "NIL", "ADVANCE");
    private static final Set<String> ECOM_KEYWORDS = Set.of(
            "amazon", "flipkart", "e-commerce", "ecommerce", "meesho", "myntra",
            "swiggy", "zomato", "shopify");

    private final OrganizationRepository organizationRepository;
    private final FilerProfileRepository filerProfileRepository;
    private final ParsedVoucherRepository parsedVoucherRepository;
    private final Gstr1FillInRepository fillInRepository;

    public Gstr1Validator(OrganizationRepository organizationRepository,
                          FilerProfileRepository filerProfileRepository,
                          ParsedVoucherRepository parsedVoucherRepository,
                          Gstr1FillInRepository fillInRepository) {
        this.organizationRepository = organizationRepository;
        this.filerProfileRepository = filerProfileRepository;
        this.parsedVoucherRepository = parsedVoucherRepository;
        this.fillInRepository = fillInRepository;
    }

    public List<ValidationResult> validate(Workflow workflow, List<PreparedReturnRow> rows) {
        List<ValidationResult> results = new ArrayList<>();
        String ownGstin = ownGstin(workflow.getOrganizationId());
        FilerProfile profile = profileFor(workflow);
        int hsnDigits = profile != null && profile.getHsnDigits() != null ? profile.getHsnDigits() : 4;
        boolean einvoiceEnabled = profile != null && profile.isEinvoiceEnabled();

        Set<String> preCaptureKeys = new HashSet<>();
        Set<String> ecomKeys = new HashSet<>();
        List<ParsedVoucher> vouchers = parsedVouchers(workflow);
        for (ParsedVoucher voucher : vouchers) {
            String key = VoucherKeys.of(voucher);
            if (voucher.getCaptureVersion() == 0) {
                preCaptureKeys.add(key);
            }
            if (isEcomOperator(voucher)) {
                ecomKeys.add(key);
            }
        }
        Map<String, Map<Gstr1FillInAttribute, String>> fillIns = fillInsByVoucher(workflow.getId());

        LocalDate periodStart = workflow.getPeriodStart();
        LocalDate periodEnd = workflow.getPeriodEnd();
        Map<String, Set<String>> invoiceNos = new LinkedHashMap<>();
        BigDecimal aggregate = BigDecimal.ZERO;

        for (PreparedReturnRow row : rows) {
            String table = row.getTableCode();
            Map<String, Object> payload = row.payload();

            checkGstin(results, row, payload, ownGstin);
            if ("HSN".equals(table)) {
                checkHsn(results, row, payload, hsnDigits);
            }
            checkPos(results, row, payload, table);
            checkRate(results, row, payload);
            checkTaxArithmetic(results, row, payload);
            checkInvoiceNumber(results, row, payload, invoiceNos);
            checkDate(results, row, payload, periodStart, periodEnd);
            if ("EXP".equals(table) && "WP".equals(text(payload, "exportType"))) {
                checkExportShippingBill(results, row, fillIns);
            }
            if ("CDNR".equals(table) || "CDNUR".equals(table)) {
                checkCreditNote(results, row, payload);
            }
            if ("NIL".equals(table)) {
                checkNilTax(results, row, payload);
            }
            if (row.getSourceVoucherKey() != null && preCaptureKeys.contains(row.getSourceVoucherKey())) {
                results.add(new ValidationResult("CAPTURE_PRE_DATES", ValidationResult.Severity.ERROR,
                        table, row.getRowKey(),
                        "This voucher came from a day book uploaded before GST capture columns existed;"
                                + " re-upload the day book and rebuild the return"));
            }
            if (row.getSourceVoucherKey() != null && ecomKeys.contains(row.getSourceVoucherKey())) {
                results.add(new ValidationResult("ECOM_WARNING", ValidationResult.Severity.WARNING,
                        table, row.getRowKey(),
                        "Party looks like an e-commerce operator; GSTR-1 Tables 14/15 (supplies through"
                                + " e-commerce operators) are not generated yet"));
            }
            BigDecimal value = tableValue(payload, table);
            if (value != null) {
                aggregate = aggregate.add(value);
            }
        }

        // Plan Q1: a voucher captured by the pre-column parser must be re-uploaded.
        // Plan Q5: Tables 14/15 are out of v1, so a possible operator party is a warning.
        // Plan Q6: with e-invoicing enabled the default build excludes IRP-reportable B2B rows.
        // IFF cap (T22): the ₹50 lakh cap of aggregate taxable value applies to IFF months only.
        if (einvoiceEnabled && rows.stream().anyMatch(r -> "B2B".equals(r.getTableCode()))) {
            results.add(new ValidationResult("EINVOICE_EXCLUDED", ValidationResult.Severity.WARNING,
                    null, null,
                    "E-invoicing is enabled for this filer, so B2B rows that are e-invoice reportable"
                            + " may be excluded from this build; confirm they are covered by IRN invoices"));
        }
        if (workflow.getType() == WorkflowType.IFF && aggregate.compareTo(IFF_CAP) > 0) {
            results.add(new ValidationResult("IFF_CAP", ValidationResult.Severity.WARNING,
                    null, null,
                    "Aggregate value for the month exceeds the ₹50 lakh IFF cap (see T22);"
                            + " Invoice Furnishing Facility limits apply before the monthly return"));
        }
        return results;
    }

    // ---------- row rules ----------

    private void checkGstin(List<ValidationResult> results, PreparedReturnRow row,
                            Map<String, Object> payload, String ownGstin) {
        String table = row.getTableCode();
        String gstin = text(payload, "gstin");
        if (gstin == null || gstin.isBlank()) {
            if ("B2B".equals(table)) {
                results.add(new ValidationResult("GSTIN_MISSING", ValidationResult.Severity.ERROR,
                        table, row.getRowKey(), "Recipient GSTIN is missing"));
            }
            return;
        }
        String value = gstin.trim().toUpperCase(Locale.ROOT);
        if (!value.matches(GSTIN_PATTERN) || !checksumValid(value)) {
            results.add(new ValidationResult("GSTIN_INVALID", ValidationResult.Severity.ERROR,
                    table, row.getRowKey(), "Recipient GSTIN is not a valid GSTIN: " + gstin));
            return;
        }
        if (ownGstin != null && value.equals(ownGstin.trim().toUpperCase(Locale.ROOT))) {
            results.add(new ValidationResult("GSTIN_SELF", ValidationResult.Severity.ERROR,
                    table, row.getRowKey(),
                    "Recipient GSTIN equals the organization's own GSTIN: " + gstin));
        }
    }

    private void checkHsn(List<ValidationResult> results, PreparedReturnRow row,
                          Map<String, Object> payload, int hsnDigits) {
        String hsn = text(payload, "hsn");
        if (hsn == null || hsn.isBlank()) {
            results.add(new ValidationResult("HSN_MISSING", ValidationResult.Severity.ERROR,
                    row.getTableCode(), row.getRowKey(), "HSN code is missing"));
            return;
        }
        // UNVERIFIED: hsnDigits comes from the filer profile (4 or 6); the portal enforces
        // the length only for certain turnover slabs, so treat shorter codes as errors
        // exactly as the plan states.
        if (hsn.trim().length() < hsnDigits) {
            results.add(new ValidationResult("HSN_DIGITS", ValidationResult.Severity.ERROR,
                    row.getTableCode(), row.getRowKey(),
                    "HSN " + hsn + " is shorter than the configured " + hsnDigits + " digits"));
        }
    }

    private void checkPos(List<ValidationResult> results, PreparedReturnRow row,
                          Map<String, Object> payload, String table) {
        String pos = text(payload, "pos");
        if (pos == null || pos.isBlank()) {
            if (POS_REQUIRED.contains(table)) {
                results.add(new ValidationResult("POS_MISSING", ValidationResult.Severity.ERROR,
                        table, row.getRowKey(), "Place of supply is missing"));
            }
            return;
        }
        if (Gstr1Rules.normalizeStateCode(pos.trim()) == null) {
            results.add(new ValidationResult("POS_INVALID", ValidationResult.Severity.ERROR,
                    table, row.getRowKey(), "Place of supply is not a valid state code: " + pos));
        }
    }

    private void checkRate(List<ValidationResult> results, PreparedReturnRow row,
                           Map<String, Object> payload) {
        BigDecimal rate = number(payload, "rate");
        if (rate == null) {
            return;
        }
        if (!Gstr1Rules.isAllowedRate(rate)) {
            results.add(new ValidationResult("RATE_INVALID", ValidationResult.Severity.ERROR,
                    row.getTableCode(), row.getRowKey(),
                    "Tax rate " + rate.toPlainString() + "% is not an allowed GST rate"));
        }
    }

    private void checkTaxArithmetic(List<ValidationResult> results, PreparedReturnRow row,
                                    Map<String, Object> payload) {
        BigDecimal taxable = number(payload, "taxableValue");
        BigDecimal rate = number(payload, "rate");
        if (taxable == null || rate == null) {
            return;
        }
        BigDecimal igst = number(payload, "igst");
        BigDecimal cgst = number(payload, "cgst");
        BigDecimal sgst = number(payload, "sgst");
        BigDecimal cess = number(payload, "cess");
        BigDecimal taxes;
        if (igst != null || cgst != null || sgst != null || cess != null) {
            taxes = orZero(igst).add(orZero(cgst)).add(orZero(sgst)).add(orZero(cess));
        } else {
            taxes = number(payload, "taxes");
            if (taxes == null) {
                return;
            }
        }
        BigDecimal expected = taxable.multiply(rate)
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        if (taxes.subtract(expected).abs().compareTo(TAX_TOLERANCE) > 0) {
            results.add(new ValidationResult("TAX_MISMATCH", ValidationResult.Severity.ERROR,
                    row.getTableCode(), row.getRowKey(),
                    "Tax " + taxes.toPlainString() + " does not match taxable value "
                            + taxable.toPlainString() + " at rate " + rate.toPlainString()
                            + "% (expected about " + expected.toPlainString() + ")"));
        }
    }

    private void checkInvoiceNumber(List<ValidationResult> results, PreparedReturnRow row,
                                    Map<String, Object> payload,
                                    Map<String, Set<String>> seenByTable) {
        for (String key : List.of("invoiceNo", "noteNo")) {
            String invoiceNo = text(payload, key);
            if (invoiceNo == null || invoiceNo.isBlank()) {
                continue;
            }
            // Review Focus 2: lowercase invoice numbers, spaces and over-long numbers are
            // reported, never silently altered to match the portal's invoice-number format.
            if (!invoiceNo.trim().matches(INVOICE_NO_PATTERN)) {
                results.add(new ValidationResult("INVOICE_NO_INVALID", ValidationResult.Severity.ERROR,
                        row.getTableCode(), row.getRowKey(),
                        "Invoice number '" + invoiceNo + "' is not uppercase alphanumeric with"
                                + " '/' and '-' only, at most 16 characters"));
            }
            Set<String> seen = seenByTable.computeIfAbsent(row.getTableCode(),
                    k -> new HashSet<>());
            String normalized = invoiceNo.trim().toUpperCase(Locale.ROOT);
            if (seen.contains(normalized)) {
                results.add(new ValidationResult("INVOICE_NO_DUPLICATE", ValidationResult.Severity.ERROR,
                        row.getTableCode(), row.getRowKey(),
                        "Invoice number '" + invoiceNo + "' appears more than once in table "
                                + row.getTableCode()));
            } else {
                seen.add(normalized);
            }
        }
    }

    private void checkDate(List<ValidationResult> results, PreparedReturnRow row,
                           Map<String, Object> payload, LocalDate periodStart, LocalDate periodEnd) {
        boolean isNote = "CDNR".equals(row.getTableCode()) || "CDNUR".equals(row.getTableCode());
        String key = isNote ? "noteDate" : "invoiceDate";
        String date = text(payload, key);
        boolean required = isNote
                || List.of("B2B", "B2CL", "EXP").contains(row.getTableCode());
        if (date == null || date.isBlank()) {
            if (required) {
                results.add(new ValidationResult("DATE_INVALID", ValidationResult.Severity.ERROR,
                        row.getTableCode(), row.getRowKey(),
                        "Invoice date is missing"));
            }
            return;
        }
        LocalDate parsed;
        try {
            parsed = LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            results.add(new ValidationResult("DATE_INVALID", ValidationResult.Severity.ERROR,
                    row.getTableCode(), row.getRowKey(),
                    "Invoice date '" + date + "' is not a valid date"));
            return;
        }
        if (parsed.isAfter(LocalDate.now())) {
            results.add(new ValidationResult("DATE_INVALID", ValidationResult.Severity.ERROR,
                    row.getTableCode(), row.getRowKey(),
                    "Invoice date '" + date + "' is in the future"));
            return;
        }
        // amendment and late-reported rows legitimately carry the original period's
        // date: the period-window check applies only to rows dated in this return
        if (row.isAmendment() || row.isLateReporting()) {
            return;
        }
        if (periodStart != null && parsed.isBefore(periodStart)
                || periodEnd != null && parsed.isAfter(periodEnd)) {
            results.add(new ValidationResult("DATE_INVALID", ValidationResult.Severity.ERROR,
                    row.getTableCode(), row.getRowKey(),
                    "Invoice date '" + date + "' is outside the return period"));
        }
    }

    private void checkExportShippingBill(List<ValidationResult> results, PreparedReturnRow row,
                                         Map<String, Map<Gstr1FillInAttribute, String>> fillIns) {
        // UNVERIFIED: the plan requires shipping bill number, date and port code for
        // exports with payment of tax; the exact portal enforcement could not be
        // confirmed, so the rule is implemented as stated.
        Map<Gstr1FillInAttribute, String> byVoucher =
                row.getSourceVoucherKey() == null ? Map.of()
                        : fillIns.getOrDefault(row.getSourceVoucherKey(), Map.of());
        List<Gstr1FillInAttribute> missing = new ArrayList<>();
        for (Gstr1FillInAttribute attribute : List.of(Gstr1FillInAttribute.SHIPPING_BILL_NO,
                Gstr1FillInAttribute.SHIPPING_BILL_DATE, Gstr1FillInAttribute.PORT_CODE)) {
            String value = byVoucher.get(attribute);
            if (value == null || value.isBlank()) {
                missing.add(attribute);
            }
        }
        if (!missing.isEmpty()) {
            results.add(new ValidationResult("EXP_SHIPPING_BILL_MISSING", ValidationResult.Severity.ERROR,
                    row.getTableCode(), row.getRowKey(),
                    "Export with payment of tax needs shipping bill details; missing "
                            + missing.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(", "))));
        }
    }

    private void checkCreditNote(List<ValidationResult> results, PreparedReturnRow row,
                                 Map<String, Object> payload) {
        List<String> problems = new ArrayList<>();
        String noteDate = text(payload, "noteDate");
        if (noteDate == null || noteDate.isBlank()) {
            problems.add("note date is missing");
        }
        BigDecimal taxable = number(payload, "taxableValue");
        if (taxable != null && taxable.signum() > 0) {
            problems.add("taxable value is not negative");
        }
        if (!problems.isEmpty()) {
            results.add(new ValidationResult("CDN_NOTE_INVALID", ValidationResult.Severity.ERROR,
                    row.getTableCode(), row.getRowKey(),
                    "Credit/debit note problem: " + String.join("; ", problems)));
        }
    }

    private void checkNilTax(List<ValidationResult> results, PreparedReturnRow row,
                             Map<String, Object> payload) {
        for (String key : List.of("igst", "cgst", "sgst", "cess")) {
            BigDecimal value = number(payload, key);
            if (value != null && value.signum() != 0) {
                results.add(new ValidationResult("NIL_TAX_PRESENT", ValidationResult.Severity.ERROR,
                        row.getTableCode(), row.getRowKey(),
                        "Nil/exempt supply carries tax (" + key + " " + value.toPlainString() + ")"));
                return;
            }
        }
    }

    // ---------- helpers ----------

    private static boolean isEcomOperator(ParsedVoucher voucher) {
        for (String source : new String[]{voucher.getPartyLedgerName(), voucher.getTypeOfSupply()}) {
            if (source == null) {
                continue;
            }
            String lower = source.toLowerCase(Locale.ROOT);
            for (String keyword : ECOM_KEYWORDS) {
                if (lower.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String ownGstin(UUID organizationId) {
        Optional<Organization> org = organizationRepository.findById(organizationId);
        return org.map(Organization::getGstin).orElse(null);
    }

    private FilerProfile profileFor(Workflow workflow) {
        List<FilerProfile> profiles = filerProfileRepository
                .findAllByOrganizationIdOrderByEffectiveFromAsc(workflow.getOrganizationId());
        LocalDate periodEnd = workflow.getPeriodEnd();
        FilerProfile applicable = null;
        for (FilerProfile profile : profiles) {
            if (profile.getEffectiveFrom() == null
                    || periodEnd == null
                    || !profile.getEffectiveFrom().isAfter(periodEnd)) {
                applicable = profile;
            }
        }
        return applicable;
    }

    private List<ParsedVoucher> parsedVouchers(Workflow workflow) {
        UUID uploadJobId = workflow.getUploadJobId();
        if (uploadJobId == null) {
            return List.of();
        }
        return parsedVoucherRepository.findByUploadJobId(uploadJobId);
    }

    private Map<String, Map<Gstr1FillInAttribute, String>> fillInsByVoucher(UUID workflowId) {
        Map<String, Map<Gstr1FillInAttribute, String>> byVoucher = new LinkedHashMap<>();
        for (Gstr1FillIn fillIn : fillInRepository.findByWorkflowIdAndScope(
                workflowId, Gstr1FillInScope.VOUCHER)) {
            byVoucher.computeIfAbsent(fillIn.getKey(), k -> new EnumMap<>(Gstr1FillInAttribute.class))
                    .put(fillIn.getAttribute(), fillIn.getValue());
        }
        return byVoucher;
    }

    private static BigDecimal tableValue(Map<String, Object> payload, String table) {
        if (List.of("B2B", "B2CL", "B2CS", "EXP", "CDNR", "CDNUR", "NIL").contains(table)) {
            return number(payload, "taxableValue");
        }
        if ("ADVANCE".equals(table)) {
            return number(payload, "advanceAmount");
        }
        return null;
    }

    private static String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof String s ? s : null;
    }

    private static BigDecimal number(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean checksumValid(String gstin) {
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int factor = GSTIN_CHARS.indexOf(gstin.charAt(i));
            if (factor < 0) {
                return false;
            }
            int weighted = factor * (i % 2 == 0 ? 1 : 2);
            sum += weighted / 36 + weighted % 36;
        }
        int check = (36 - sum % 36) % 36;
        return GSTIN_CHARS.indexOf(gstin.charAt(14)) == check;
    }
}
