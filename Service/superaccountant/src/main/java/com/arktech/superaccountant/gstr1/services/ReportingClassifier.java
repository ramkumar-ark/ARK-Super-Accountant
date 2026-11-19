package com.arktech.superaccountant.gstr1.services;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;

/**
 * Decides which GSTR-1 table a captured voucher belongs to.
 *
 * <p>Rule precedence (spec §7, B2CL threshold fixed at 2,50,000):
 * <ol>
 *   <li>cancelled voucher → NOT_REPORTED</li>
 *   <li>advance payment / adjustment vouchers → ADVANCE / ADV_ADJ</li>
 *   <li>missing place of supply → NOT_REPORTED with reason</li>
 *   <li>credit/debit note: GSTIN → CDNR, none → CDNUR</li>
 *   <li>counterparty GSTIN → B2B</li>
 *   <li>export/SEZ via fill-in or captured supply nature / foreign consignee → EXP</li>
 *   <li>unregistered + inter-state + value &gt; 2,50,000 → B2CL</li>
 *   <li>any other unregistered supply → B2CS</li>
 * </ol>
 */
public class ReportingClassifier {

    /** Fill-in resolver seam backed by Gstr1FillInService.resolve. */
    @FunctionalInterface
    public interface FillInResolver {
        Optional<String> resolve(VoucherView voucher, Gstr1FillInAttribute attribute);
    }

    private static final Set<String> NOTE_VOUCHER_TYPES =
            Set.of("credit note", "debit note");
    private static final Set<String> EXPORT_NATURES =
            Set.of("export", "export with payment", "export without payment", "sez");
    private static final Set<String> ADVANCE_TYPES =
            Set.of("advance payment", "advance adjustment", "advance receipt");

    private final FillInResolver fillInResolver;

    public ReportingClassifier(FillInResolver fillInResolver) {
        this.fillInResolver = Objects.requireNonNull(fillInResolver);
    }

    public record Result(ReportingClass category, String reason) {
        public static Result of(ReportingClass category) {
            return new Result(category, null);
        }

        public static Result notReported(String reason) {
            return new Result(ReportingClass.NOT_REPORTED, reason);
        }
    }

    public Result classify(VoucherView v, YearMonth period) {
        if (v.isCancelled()) {
            return Result.notReported("Voucher is cancelled");
        }
        String typeName = lower(v.getVoucherTypeName());
        if (ADVANCE_TYPES.contains(typeName)) {
            return Result.of(typeName.contains("adjustment") ? ReportingClass.ADV_ADJ
                    : ReportingClass.ADVANCE);
        }
        String pos = firstNonBlank(
                fillIn(v, Gstr1FillInAttribute.POS), v.getPlaceOfSupply());
        if (isBlank(pos)) {
            return Result.notReported("Place of supply is missing");
        }

        boolean hasGstin = !isBlank(v.getPartyGstin());
        if (NOTE_VOUCHER_TYPES.contains(typeName)) {
            return Result.of(hasGstin ? ReportingClass.CDNR : ReportingClass.CDNUR);
        }
        if (hasGstin) {
            return Result.of(ReportingClass.B2B);
        }

        String nature = firstNonBlank(
                fillIn(v, Gstr1FillInAttribute.SUPPLY_NATURE), v.getSupplyNature());
        String exportType = firstNonBlank(
                fillIn(v, Gstr1FillInAttribute.EXPORT_TYPE), v.getTypeOfSupply());
        boolean foreignConsignee = !isBlank(v.getConsigneeCountry())
                && !"india".equals(lower(v.getConsigneeCountry()));
        if (EXPORT_NATURES.contains(lower(nature))
                || EXPORT_NATURES.contains(lower(exportType))
                || foreignConsignee) {
            return Result.of(ReportingClass.EXP);
        }

        String posCode = Gstr1Rules.normalizeStateCode(pos);
        if (posCode == null) {
            posCode = lower(pos);
        }
        String supplierCode = lower(v.getSupplierStateCode());
        boolean interState = !posCode.equals(supplierCode);
        BigDecimal value = v.getTotalValue() == null ? BigDecimal.ZERO : v.getTotalValue();
        if (interState && value.compareTo(Gstr1Rules.b2clThreshold(period)) > 0) {
            return Result.of(ReportingClass.B2CL);
        }
        return Result.of(ReportingClass.B2CS);
    }

    private String fillIn(VoucherView v, Gstr1FillInAttribute attribute) {
        return fillInResolver.resolve(v, attribute).orElse(null);
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String firstNonBlank(String a, String b) {
        return isBlank(a) ? b : a;
    }
}
