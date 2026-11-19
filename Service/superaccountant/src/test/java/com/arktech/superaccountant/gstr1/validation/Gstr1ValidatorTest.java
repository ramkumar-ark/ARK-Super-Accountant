package com.arktech.superaccountant.gstr1.validation;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gstr1.models.Gstr1FillIn;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInScope;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.repository.Gstr1FillInRepository;
import com.arktech.superaccountant.gstr1.services.tables.TableTestSupport;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.workflow.models.FilerProfile;
import com.arktech.superaccountant.workflow.models.FilerFrequency;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.repository.FilerProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Gstr1ValidatorTest extends TableTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String OWN_GSTIN = "29AAKCS4712P1ZO";
    private static final String OTHER_GSTIN = "27AAPFU0939F1ZV";

    private OrganizationRepository organizationRepository;
    private FilerProfileRepository filerProfileRepository;
    private ParsedVoucherRepository parsedVoucherRepository;
    private Gstr1FillInRepository fillInRepository;
    private Gstr1Validator validator;

    @BeforeEach
    void setUp() {
        organizationRepository = mock(OrganizationRepository.class);
        filerProfileRepository = mock(FilerProfileRepository.class);
        parsedVoucherRepository = mock(ParsedVoucherRepository.class);
        fillInRepository = mock(Gstr1FillInRepository.class);
        Organization org = new Organization();
        org.setGstin(OWN_GSTIN);
        when(organizationRepository.findById(any(UUID.class))).thenReturn(Optional.of(org));
        when(filerProfileRepository.findAllByOrganizationIdOrderByEffectiveFromAsc(any(UUID.class)))
                .thenReturn(List.of());
        when(parsedVoucherRepository.findByUploadJobId(any(UUID.class))).thenReturn(List.of());
        when(fillInRepository.findByWorkflowIdAndScope(any(UUID.class), any(Gstr1FillInScope.class)))
                .thenReturn(List.of());
        validator = new Gstr1Validator(organizationRepository, filerProfileRepository,
                parsedVoucherRepository, fillInRepository);
    }

    private PreparedReturnRow row(String tableCode, String rowKey, String sourceVoucherKey,
                                  Map<String, Object> payload) {
        try {
            PreparedReturnRow r = new PreparedReturnRow();
            r.setTableCode(tableCode);
            r.setRowKey(rowKey);
            r.setSourceVoucherKey(sourceVoucherKey);
            r.setPayload(MAPPER.writeValueAsString(payload));
            return r;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static BigDecimal n(String s) {
        return new BigDecimal(s);
    }

    private Map<String, Object> b2b(Object... extra) {
        Map<String, Object> base = payload(
                "gstin", OTHER_GSTIN,
                "invoiceNo", "INV-001",
                "invoiceDate", "2025-04-10",
                "pos", "27",
                "rate", n("18"),
                "taxableValue", n("10000"),
                "igst", n("1800"), "cgst", n("0"), "sgst", n("0"), "cess", n("0"),
                "invoiceValue", n("11800"));
        for (int i = 0; i < extra.length; i += 2) {
            if (extra[i] == null) {
                base.remove((String) extra[i + 1]);
            } else {
                base.put((String) extra[i], extra[i + 1]);
            }
        }
        return base;
    }

    private List<ValidationResult> validate(PreparedReturnRow... rows) {
        Workflow w = workflow();
        w.setUploadJobId(UUID.randomUUID());
        return validator.validate(w, List.of(rows));
    }

    private boolean has(List<ValidationResult> results, String code) {
        return results.stream().anyMatch(r -> r.code().equals(code));
    }

    private long count(List<ValidationResult> results, String code) {
        return results.stream().filter(r -> r.code().equals(code)).count();
    }

    @Test
    void b2bGstinMissing_isError() {
        List<ValidationResult> results = validate(
                row("B2B", "b2b:G:g1", "G:g1", b2b(null, "gstin")));
        assertThat(count(results, "GSTIN_MISSING")).isEqualTo(1);
        assertThat(results.stream()
                .filter(r -> r.code().equals("GSTIN_MISSING"))
                .allMatch(r -> r.severity() == ValidationResult.Severity.ERROR)).isTrue();
    }

    @Test
    void b2bGstinBadFormatOrChecksum_isError() {
        assertThat(has(validate(row("B2B", "b2b:G:g1", "G:g1",
                b2b("gstin", "29AAKCS4712P1Z"))), "GSTIN_INVALID")).isTrue();
        assertThat(has(validate(row("B2B", "b2b:G:g2", "G:g2",
                b2b("gstin", "29AAKCS4712P1ZA"))), "GSTIN_INVALID")).isTrue();
        assertThat(has(validate(row("B2B", "b2b:G:g3", "G:g3",
                b2b("gstin", OTHER_GSTIN))), "GSTIN_INVALID")).isFalse();
    }

    @Test
    void hsnMissingOrTooShort_isError() {
        assertThat(has(validate(row("HSN", "hsn:9963: nos:18:b2b",
                null, payload("hsn", "", "uqc", "NOS", "quantity", n("1"),
                        "rate", n("18"), "supplyType", "B2B",
                        "taxableValue", n("10000"), "taxes", n("1800"),
                        "invoiceValue", n("11800")))), "HSN_MISSING")).isTrue();

        FilerProfile six = new FilerProfile();
        six.setHsnDigits(6);
        six.setFrequency(FilerFrequency.MONTHLY);
        when(filerProfileRepository.findAllByOrganizationIdOrderByEffectiveFromAsc(any(UUID.class)))
                .thenReturn(List.of(six));
        assertThat(has(validate(row("HSN", "hsn:9963: nos:18:b2b",
                null, payload("hsn", "9963", "uqc", "NOS", "quantity", n("1"),
                        "rate", n("18"), "supplyType", "B2B",
                        "taxableValue", n("10000"), "taxes", n("1800"),
                        "invoiceValue", n("11800")))), "HSN_DIGITS")).isTrue();
        assertThat(has(validate(row("HSN", "hsn:996334: nos:18:b2b",
                null, payload("hsn", "996334", "uqc", "NOS", "quantity", n("1"),
                        "rate", n("18"), "supplyType", "B2B",
                        "taxableValue", n("10000"), "taxes", n("1800"),
                        "invoiceValue", n("11800")))), "HSN_DIGITS")).isFalse();
    }

    @Test
    void placeOfSupplyMissingOrInvalid_isError() {
        assertThat(has(validate(row("B2B", "b2b:G:g1", "G:g1", b2b(null, "pos"))),
                "POS_MISSING")).isTrue();
        assertThat(has(validate(row("B2CS", "b2cs:99:18", null,
                payload("pos", "99", "rate", n("18"), "taxableValue", n("1000"),
                        "igst", n("180"), "cgst", n("0"), "sgst", n("0"), "cess", n("0"),
                        "invoiceValue", n("1180")))), "POS_INVALID")).isTrue();
        assertThat(has(validate(row("B2CS", "b2cs:29:18", null,
                payload("pos", "29", "rate", n("18"), "taxableValue", n("1000"),
                        "igst", n("0"), "cgst", n("90"), "sgst", n("90"), "cess", n("0"),
                        "invoiceValue", n("1180")))), "POS_INVALID")).isFalse();
    }

    @Test
    void rateOutsideAllowedSet_isError() {
        assertThat(has(validate(row("B2CS", "b2cs:29:2.5", null,
                payload("pos", "29", "rate", n("2.5"), "taxableValue", n("1000"),
                        "igst", n("0"), "cgst", n("0"), "sgst", n("0"), "cess", n("0"),
                        "invoiceValue", n("1000")))), "RATE_INVALID")).isTrue();
        assertThat(has(validate(row("B2CS", "b2cs:29:18", null,
                payload("pos", "29", "rate", n("18"), "taxableValue", n("1000"),
                        "igst", n("180"), "cgst", n("0"), "sgst", n("0"), "cess", n("0"),
                        "invoiceValue", n("1180")))), "RATE_INVALID")).isFalse();
    }

    @Test
    void taxArithmeticMismatch_isError_withinTolerancePasses() {
        assertThat(has(validate(row("B2B", "b2b:G:g1", "G:g1",
                b2b("igst", n("1900")))), "TAX_MISMATCH")).isTrue();
        assertThat(has(validate(row("B2B", "b2b:G:g2", "G:g2",
                b2b("igst", n("1799.5")))), "TAX_MISMATCH")).isFalse();
    }

    @Test
    void invoiceNumberInvalidOrDuplicated_isError() {
        assertThat(has(validate(row("B2B", "b2b:G:g1", "G:g1", b2b("invoiceNo", "inv 001"))),
                "INVOICE_NO_INVALID")).isTrue();
        assertThat(has(validate(row("B2B", "b2b:G:g2", "G:g2", b2b("invoiceNo", "inv001"))),
                "INVOICE_NO_INVALID")).isTrue();
        assertThat(has(validate(row("B2B", "b2b:G:g3", "G:g3",
                b2b("invoiceNo", "A-VERY-LONG-INVOICE-NO-1234"))), "INVOICE_NO_INVALID")).isTrue();
        assertThat(has(validate(row("B2B", "b2b:G:g4", "G:g4", b2b("invoiceNo", "INV/2025-04/001"))),
                "INVOICE_NO_INVALID")).isFalse();

        List<ValidationResult> duplicates = validate(
                row("B2B", "b2b:G:g5", "G:g5", b2b("invoiceNo", "INV-DUP")),
                row("B2B", "b2b:G:g6", "G:g6", b2b("invoiceNo", "INV-DUP")));
        assertThat(count(duplicates, "INVOICE_NO_DUPLICATE")).isEqualTo(1);
        assertThat(duplicates.stream()
                .filter(r -> r.code().equals("INVOICE_NO_DUPLICATE"))
                .allMatch(r -> r.severity() == ValidationResult.Severity.ERROR)).isTrue();
        assertThat(has(validate(
                row("B2B", "b2b:G:g7", "G:g7", b2b("invoiceNo", "INV-DUP")),
                row("EXPWP", "exp:G:g8", "G:g8",
                        payload("invoiceNo", "INV-DUP", "invoiceDate", "2025-04-10",
                                "exportType", "WP", "country", "United States",
                                "rate", n("0"), "taxableValue", n("1000"),
                                "igst", n("0"), "cgst", n("0"), "sgst", n("0"), "cess", n("0"),
                                "invoiceValue", n("1000")))), "INVOICE_NO_DUPLICATE")).isFalse();
    }

    @Test
    void invoiceDateMissingOrOutsidePeriod_isError() {
        assertThat(has(validate(row("B2B", "b2b:G:g1", "G:g1", b2b(null, "invoiceDate"))),
                "DATE_INVALID")).isTrue();
        assertThat(has(validate(row("B2B", "b2b:G:g2", "G:g2", b2b("invoiceDate", "2025-03-31"))),
                "DATE_INVALID")).isTrue();
        assertThat(has(validate(row("B2B", "b2b:G:g3", "G:g3", b2b("invoiceDate", "2025-05-01"))),
                "DATE_INVALID")).isTrue();
        assertThat(has(validate(row("B2B", "b2b:G:g4", "G:g4", b2b("invoiceDate", "not-a-date"))),
                "DATE_INVALID")).isTrue();
        assertThat(has(validate(row("B2B", "b2b:G:g5", "G:g5", b2b("invoiceDate", "2025-04-30"))),
                "DATE_INVALID")).isFalse();
    }

    @Test
    void recipientGstinEqualsOwnGstin_isError() {
        assertThat(has(validate(row("B2B", "b2b:G:g1", "G:g1", b2b("gstin", OWN_GSTIN))),
                "GSTIN_SELF")).isTrue();
        assertThat(has(validate(row("B2B", "b2b:G:g2", "G:g2", b2b("gstin", OTHER_GSTIN))),
                "GSTIN_SELF")).isFalse();
    }

    @Test
    void exportWithoutPlaceOfSupplyMissingShippingBill_isError_whenWithPayment() {
        assertThat(has(validate(row("EXPWP", "exp:G:g1", "G:g1",
                payload("invoiceNo", "EXP-001", "invoiceDate", "2025-04-10",
                        "exportType", "WP", "country", "United States",
                        "rate", n("0"), "taxableValue", n("1000"),
                        "igst", n("0"), "cgst", n("0"), "sgst", n("0"), "cess", n("0"),
                        "invoiceValue", n("1000")))), "EXP_SHIPPING_BILL_MISSING")).isTrue();

        Gstr1FillIn billNo = fillIn("G:g1", Gstr1FillInAttribute.SHIPPING_BILL_NO, "SB/123");
        Gstr1FillIn billDate = fillIn("G:g1", Gstr1FillInAttribute.SHIPPING_BILL_DATE, "2025-04-11");
        Gstr1FillIn portCode = fillIn("G:g1", Gstr1FillInAttribute.PORT_CODE, "INMAA1");
        when(fillInRepository.findByWorkflowIdAndScope(any(UUID.class),
                org.mockito.ArgumentMatchers.eq(Gstr1FillInScope.VOUCHER)))
                .thenReturn(List.of(billNo, billDate, portCode));
        assertThat(has(validate(row("EXPWP", "exp:G:g1", "G:g1",
                payload("invoiceNo", "EXP-001", "invoiceDate", "2025-04-10",
                        "exportType", "WP", "country", "United States",
                        "rate", n("0"), "taxableValue", n("1000"),
                        "igst", n("0"), "cgst", n("0"), "sgst", n("0"), "cess", n("0"),
                        "invoiceValue", n("1000")))), "EXP_SHIPPING_BILL_MISSING")).isFalse();

        assertThat(has(validate(row("EXPWOP", "exp:G:g2", "G:g2",
                payload("invoiceNo", "EXP-002", "invoiceDate", "2025-04-10",
                        "exportType", "WOP", "country", "United States",
                        "rate", n("0"), "taxableValue", n("1000"),
                        "igst", n("0"), "cgst", n("0"), "sgst", n("0"), "cess", n("0"),
                        "invoiceValue", n("1000")))), "EXP_SHIPPING_BILL_MISSING")).isFalse();
    }

    @Test
    void creditNoteWithoutDateOrPositiveTaxable_isError() {
        assertThat(has(validate(row("CDN", "cdn:G:g1", "G:g1",
                payload("noteType", "credit note", "gstin", OTHER_GSTIN,
                        "noteNo", "CN-001", "rate", n("18"),
                        "taxableValue", n("-1000"), "taxes", n("-180"),
                        "invoiceValue", n("-1180")))), "CDN_NOTE_INVALID")).isTrue();
        assertThat(has(validate(row("CDN", "cdn:G:g2", "G:g2",
                payload("noteType", "credit note", "gstin", OTHER_GSTIN,
                        "noteNo", "CN-002", "noteDate", "2025-04-12", "rate", n("18"),
                        "taxableValue", n("1000"), "taxes", n("180"),
                        "invoiceValue", n("1180")))), "CDN_NOTE_INVALID")).isTrue();
        assertThat(has(validate(row("CDN", "cdn:G:g3", "G:g3",
                payload("noteType", "credit note", "gstin", OTHER_GSTIN,
                        "noteNo", "CN-003", "noteDate", "2025-04-12", "rate", n("18"),
                        "taxableValue", n("-1000"), "taxes", n("-180"),
                        "invoiceValue", n("-1180")))), "CDN_NOTE_INVALID")).isFalse();
    }

    @Test
    void voucherPredatesCaptureColumns_isError() {
        ParsedVoucher old = new ParsedVoucher();
        old.setVoucherGuid("g1");
        old.setVoucherDate(LocalDate.of(2025, 4, 10));
        old.setCaptureVersion(0);
        ParsedVoucher fresh = new ParsedVoucher();
        fresh.setVoucherGuid("g2");
        fresh.setVoucherDate(LocalDate.of(2025, 4, 10));
        fresh.setCaptureVersion(1);
        when(parsedVoucherRepository.findByUploadJobId(any(UUID.class)))
                .thenReturn(List.of(old, fresh));

        List<ValidationResult> results = validate(
                row("B2B", "b2b:G:g1", "G:g1", b2b()),
                row("B2B", "b2b:G:g2", "G:g2", b2b()));
        assertThat(count(results, "CAPTURE_PRE_DATES")).isEqualTo(1);
        assertThat(results.stream()
                .filter(r -> r.code().equals("CAPTURE_PRE_DATES"))
                .allMatch(r -> r.severity() == ValidationResult.Severity.ERROR)).isTrue();
    }

    @Test
    void possibleEcommerceOperatorParty_isWarning() {
        ParsedVoucher operator = new ParsedVoucher();
        operator.setVoucherGuid("g1");
        operator.setVoucherDate(LocalDate.of(2025, 4, 10));
        operator.setCaptureVersion(1);
        operator.setPartyLedgerName("Amazon Seller Services");
        when(parsedVoucherRepository.findByUploadJobId(any(UUID.class)))
                .thenReturn(List.of(operator));

        List<ValidationResult> results = validate(row("B2B", "b2b:G:g1", "G:g1", b2b()));
        assertThat(count(results, "ECOM_WARNING")).isEqualTo(1);
        assertThat(results.stream()
                .filter(r -> r.code().equals("ECOM_WARNING"))
                .allMatch(r -> r.severity() == ValidationResult.Severity.WARNING)).isTrue();
    }

    @Test
    void aggregateValueAboveIffCap_isWarning() {
        List<ValidationResult> results = validate(
                row("B2B", "b2b:G:g1", "G:g1", b2b("taxableValue", n("3000000"))),
                row("B2B", "b2b:G:g2", "G:g2", b2b("taxableValue", n("2500000"))));
        assertThat(count(results, "IFF_CAP")).isEqualTo(1);
        assertThat(results.stream()
                .filter(r -> r.code().equals("IFF_CAP"))
                .allMatch(r -> r.severity() == ValidationResult.Severity.WARNING)).isTrue();
        assertThat(has(validate(row("B2B", "b2b:G:g3", "G:g3",
                b2b("taxableValue", n("1000")))), "IFF_CAP")).isFalse();
    }

    @Test
    void nilExemptRowWithNonZeroTax_isError() {
        assertThat(has(validate(row("NIL", "nil:29", null,
                payload("pos", "29", "taxableValue", n("5000"), "igst", n("900")))),
                "NIL_TAX_PRESENT")).isTrue();
        assertThat(has(validate(row("NIL", "nil:29", null,
                payload("pos", "29", "taxableValue", n("5000")))), "NIL_TAX_PRESENT")).isFalse();
    }

    @Test
    void einvoiceFilerWithB2bRows_isWarning() {
        FilerProfile einv = new FilerProfile();
        einv.setHsnDigits(4);
        einv.setEinvoiceEnabled(true);
        einv.setFrequency(FilerFrequency.MONTHLY);
        when(filerProfileRepository.findAllByOrganizationIdOrderByEffectiveFromAsc(any(UUID.class)))
                .thenReturn(List.of(einv));

        List<ValidationResult> results = validate(row("B2B", "b2b:G:g1", "G:g1", b2b()));
        assertThat(count(results, "EINVOICE_EXCLUDED")).isEqualTo(1);
        assertThat(results.stream()
                .filter(r -> r.code().equals("EINVOICE_EXCLUDED"))
                .allMatch(r -> r.severity() == ValidationResult.Severity.WARNING)).isTrue();
        assertThat(has(validate(row("B2CS", "b2cs:29:18", null,
                payload("pos", "29", "rate", n("18"), "taxableValue", n("1000"),
                        "igst", n("180"), "cgst", n("0"), "sgst", n("0"), "cess", n("0"),
                        "invoiceValue", n("1180")))), "EINVOICE_EXCLUDED")).isFalse();
    }

    @Test
    void cleanRows_produceNoResults() {
        assertThat(validator.validate(workflow(), List.of())).isEmpty();
        assertThat(validate(row("B2B", "b2b:G:g1", "G:g1", b2b()))).isEmpty();
    }

    private Gstr1FillIn fillIn(String key, Gstr1FillInAttribute attribute, String value) {
        Gstr1FillIn f = new Gstr1FillIn();
        f.setScope(Gstr1FillInScope.VOUCHER);
        f.setKey(key);
        f.setAttribute(attribute);
        f.setValue(value);
        return f;
    }
}
