package com.arktech.superaccountant.gstr1.services;

import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.models.ParsedVoucherItem;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gst.mapping.GstHead;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMapping;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingRepository;
import com.arktech.superaccountant.gst.mapping.TaxLedgerRole;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.snapshot.VoucherKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.function.Function;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds {@link VoucherView}s from the captured day book for a workflow.
 *
 * <p>Amount derivation from captured entities (T8 review critical item):
 * <ul>
 *   <li>taxable value: sum of |amount| over entries carrying a GST rate, falling back to
 *       invoice value minus mapped tax heads when no rated entry exists</li>
 *   <li>rate: the first rated entry's GST rate</li>
 *   <li>igst/cgst/sgst/cess: |amount| summed over entries whose ledger is mapped to a
 *       voucher-tax role and head (TaxLedgerMapping, same convention as the GST rules)</li>
 *   <li>HSN: Gstr1FillInService override (voucher then ledger scope), else
 *       UploadLedgerSnapshot.hsnSacCode of the entry's ledger</li>
 *   <li>UQC/quantity: Gstr1FillInService resolve (voucher then ledger scope)</li>
 *   <li>place of supply: Gstr1FillInService override, else the captured value;
 *       normalized to a state code by VoucherView.placeOfSupplyCode()</li>
 *   <li>invoice value: the voucher's balanced-side total (totalDebit)</li>
 * </ul>
 * Vouchers with a null date or dates outside the workflow period are excluded.
 */
@Service
public class VoucherViewBuilder {

    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;

    @Autowired
    private UploadLedgerSnapshotRepository snapshotRepository;

    @Autowired
    private TaxLedgerMappingRepository taxLedgerMappingRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private Gstr1FillInService fillInService;

    public VoucherViewBuilder() {
    }

    public VoucherViewBuilder(ParsedVoucherRepository parsedVoucherRepository,
                              UploadLedgerSnapshotRepository snapshotRepository,
                              TaxLedgerMappingRepository taxLedgerMappingRepository,
                              OrganizationRepository organizationRepository,
                              Gstr1FillInService fillInService) {
        this.parsedVoucherRepository = parsedVoucherRepository;
        this.snapshotRepository = snapshotRepository;
        this.taxLedgerMappingRepository = taxLedgerMappingRepository;
        this.organizationRepository = organizationRepository;
        this.fillInService = fillInService;
    }

    public List<VoucherView> build(Workflow workflow) {
        return views(workflow, true);
    }

    /**
     * Same as {@link #build} but without the period filter: amendment builders
     * (T20) must see the current books' view of a voucher dated in an earlier
     * period — e.g. an April invoice corrected after April's return was frozen.
     */
    public List<VoucherView> buildAll(Workflow workflow) {
        return views(workflow, false);
    }

    private List<VoucherView> views(Workflow workflow, boolean restrictToPeriod) {
        if (workflow == null || workflow.getUploadJobId() == null) {
            return List.of();
        }
        UUID organizationId = workflow.getOrganizationId();
        String supplierStateCode = supplierStateCode(organizationId);
        Map<String, TaxLedgerMapping> taxLedgers = taxLedgersByLedgerKey(organizationId);
        Map<String, UploadLedgerSnapshot> snapshots = snapshotsByLedgerKey(workflow.getUploadJobId());

        List<ParsedVoucher> vouchers = parsedVoucherRepository
                .findByUploadJobId(workflow.getUploadJobId()).stream()
                .filter(v -> !restrictToPeriod || (v.getVoucherDate() != null
                        && !v.getVoucherDate().isBefore(workflow.getPeriodStart())
                        && !v.getVoucherDate().isAfter(workflow.getPeriodEnd())))
                .toList();

        List<VoucherView> views = new ArrayList<>();
        for (Map.Entry<String, ParsedVoucher> keyed : VoucherKeys.keyAll(vouchers).entrySet()) {
            views.add(view(workflow, keyed.getKey(), keyed.getValue(), supplierStateCode, taxLedgers, snapshots));
        }
        return views;
    }

    private VoucherView view(Workflow workflow, String voucherKey, ParsedVoucher voucher, String supplierStateCode,
                             Map<String, TaxLedgerMapping> taxLedgers,
                             Map<String, UploadLedgerSnapshot> snapshots) {
        List<ParsedVoucherEntry> entries = voucher.getEntries() == null
                ? List.of() : voucher.getEntries();
        List<ParsedVoucherItem> items = voucher.getItems() == null
                ? List.of() : voucher.getItems();

        BigDecimal totalValue = totalValue(voucher);

        Map<GstHead, BigDecimal> heads = new HashMap<>();
        BigDecimal taxableValue = BigDecimal.ZERO;
        BigDecimal rate = null;
        for (ParsedVoucherEntry e : entries) {
            if (e.getAmount() == null) {
                continue;
            }
            GstHead head = head(e.getLedgerName(), taxLedgers);
            if (head != null) {
                heads.merge(head, e.getAmount().abs(), BigDecimal::add);
            }
            if (e.getGstRate() != null) {
                taxableValue = taxableValue.add(e.getAmount().abs());
                if (rate == null) {
                    rate = e.getGstRate();
                }
            }
        }
        if (rate == null && taxableValue.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal totalTax = heads.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal derived = totalValue.subtract(totalTax);
            taxableValue = derived.max(BigDecimal.ZERO);
        }

        List<ParsedVoucherItem> uniformItems = uniformHsnUqcItems(items) ? items : List.of();

        Optional<String> hsnValue = resolveHsn(workflow, voucherKey, entries, snapshots);
        Optional<String> uqcValue = resolveText(workflow, voucherKey, entries, Gstr1FillInAttribute.UQC);
        Optional<BigDecimal> qtyValue = resolveText(workflow, voucherKey, entries, Gstr1FillInAttribute.QTY)
                .map(VoucherViewBuilder::parseAmount);

        // Item-level HSN breakdown is a pure captured-data path: only when no
        // fill-in or ledger snapshot resolves HSN/UQC/quantity anywhere does the
        // mixed-HSN voucher split into per-item contributions.
        List<VoucherView.HsnItem> hsnItems = hsnValue.isEmpty() && uqcValue.isEmpty() && qtyValue.isEmpty()
                && !uniformHsnUqcItems(items)
                ? allocateHsnItems(items, taxableValue, totalValue, heads)
                : List.of();

        VoucherView.Builder b = new VoucherView.Builder()
                .voucherKey(voucherKey)
                .voucherTypeName(voucher.getVoucherTypeName())
                .partyGstin(voucher.getPartyGstin())
                .partyGstRegistrationType(voucher.getPartyGstRegistrationType())
                .placeOfSupply(resolveText(workflow, voucherKey, entries, Gstr1FillInAttribute.POS)
                        .orElse(voucher.getPlaceOfSupply()))
                .consigneeState(voucher.getConsigneeState())
                .consigneeCountry(voucher.getConsigneeCountry())
                .supplyNature(voucher.getSupplyNature())
                .typeOfSupply(voucher.getTypeOfSupply())
                .supplierStateCode(supplierStateCode)
                .totalValue(totalValue)
                .isCancelled(voucher.isCancelled())
                .voucherNumber(voucher.getVoucherNumber())
                .voucherDate(voucher.getVoucherDate())
                .taxableValue(taxableValue)
                .rate(rate)
                .igst(heads.getOrDefault(GstHead.IGST, BigDecimal.ZERO))
                .cgst(heads.getOrDefault(GstHead.CGST, BigDecimal.ZERO))
                .sgst(heads.getOrDefault(GstHead.SGST, BigDecimal.ZERO))
                .cess(heads.getOrDefault(GstHead.CESS, BigDecimal.ZERO))
                .hsn(hsnValue
                        .orElseGet(() -> firstItemValue(uniformItems, ParsedVoucherItem::getHsn)))
                .uqc(uqcValue
                        .orElseGet(() -> firstItemValue(uniformItems, ParsedVoucherItem::getUqc)))
                .quantity(qtyValue
                        .orElseGet(() -> sumItemQuantities(uniformItems)))
                .hsnItems(hsnItems);
        return b.build();
    }

    /**
     * Splits a mixed-HSN voucher's taxable value and tax heads across its
     * inventory items, pro-rata by |item amount| (equal weights when no item
     * carries an amount), rounded to 2dp with the rounding residual added to
     * the largest-weight item so sums reconcile exactly to the voucher totals.
     * Items with a blank HSN are skipped.
     */
    private static List<VoucherView.HsnItem> allocateHsnItems(List<ParsedVoucherItem> items,
                                                              BigDecimal taxableValue,
                                                              BigDecimal totalValue,
                                                              Map<GstHead, BigDecimal> heads) {
        List<ParsedVoucherItem> rated = items.stream()
                .filter(i -> i.getHsn() != null && !i.getHsn().isBlank())
                .toList();
        if (rated.isEmpty()) {
            return List.of();
        }
        BigDecimal[] weights = new BigDecimal[rated.size()];
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (int i = 0; i < rated.size(); i++) {
            BigDecimal amount = rated.get(i).getAmount();
            weights[i] = amount == null ? BigDecimal.ZERO : amount.abs();
            totalWeight = totalWeight.add(weights[i]);
        }
        boolean equalSplit = totalWeight.compareTo(BigDecimal.ZERO) == 0;
        if (equalSplit) {
            for (int i = 0; i < rated.size(); i++) {
                weights[i] = BigDecimal.ONE;
            }
            totalWeight = new BigDecimal(rated.size());
        }
        int largest = 0;
        for (int i = 1; i < rated.size(); i++) {
            if (weights[i].compareTo(weights[largest]) > 0) {
                largest = i;
            }
        }
        BigDecimal[] taxable = allocate(taxableValue, weights, totalWeight, largest);
        BigDecimal[] invoice = allocate(totalValue == null ? BigDecimal.ZERO : totalValue,
                weights, totalWeight, largest);
        BigDecimal[] igst = allocate(heads.getOrDefault(GstHead.IGST, BigDecimal.ZERO), weights, totalWeight, largest);
        BigDecimal[] cgst = allocate(heads.getOrDefault(GstHead.CGST, BigDecimal.ZERO), weights, totalWeight, largest);
        BigDecimal[] sgst = allocate(heads.getOrDefault(GstHead.SGST, BigDecimal.ZERO), weights, totalWeight, largest);
        BigDecimal[] cess = allocate(heads.getOrDefault(GstHead.CESS, BigDecimal.ZERO), weights, totalWeight, largest);

        List<VoucherView.HsnItem> result = new ArrayList<>();
        for (int i = 0; i < rated.size(); i++) {
            ParsedVoucherItem item = rated.get(i);
            result.add(new VoucherView.HsnItem(
                    item.getHsn().trim(),
                    item.getUqc() == null ? null : item.getUqc().trim(),
                    item.getQuantity(),
                    taxable[i], igst[i], cgst[i], sgst[i], cess[i], invoice[i]));
        }
        return result;
    }

    private static BigDecimal[] allocate(BigDecimal total, BigDecimal[] weights,
                                         BigDecimal totalWeight, int largest) {
        BigDecimal[] shares = new BigDecimal[weights.length];
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < weights.length; i++) {
            BigDecimal share = total.multiply(weights[i])
                    .divide(totalWeight, 2, java.math.RoundingMode.HALF_UP);
            shares[i] = share;
            allocated = allocated.add(share);
        }
        shares[largest] = shares[largest].add(total.subtract(allocated));
        return shares;
    }

    private Optional<String> resolveHsn(Workflow workflow, String voucherKey, List<ParsedVoucherEntry> entries,
                                        Map<String, UploadLedgerSnapshot> snapshots) {
        Optional<String> voucherScope = fillInService
                .resolve(workflow, voucherKey, null, Gstr1FillInAttribute.HSN);
        if (voucherScope.isPresent()) {
            return voucherScope;
        }
        for (ParsedVoucherEntry e : entries) {
            Optional<String> override = e.getLedgerName() == null ? Optional.empty()
                    : fillInService.resolve(workflow, voucherKey, e.getLedgerName(),
                            Gstr1FillInAttribute.HSN);
            if (override.isPresent()) {
                return override;
            }
            UploadLedgerSnapshot snapshot = snapshots.get(key(e.getLedgerName()));
            if (snapshot != null && snapshot.getHsnSacCode() != null
                    && !snapshot.getHsnSacCode().isBlank()) {
                return Optional.of(snapshot.getHsnSacCode().trim());
            }
        }
        return Optional.empty();
    }

    private static boolean uniformHsnUqcItems(List<ParsedVoucherItem> items) {
        return distinctTrimmed(items, ParsedVoucherItem::getHsn) <= 1
                && distinctTrimmed(items, ParsedVoucherItem::getUqc) <= 1;
    }

    private static long distinctTrimmed(List<ParsedVoucherItem> items,
                                        Function<ParsedVoucherItem, String> getter) {
        return items.stream()
                .map(getter)
                .map(v -> v == null ? "" : v.trim())
                .distinct()
                .count();
    }

    // Item-level fallback: used only when fill-ins and ledger snapshots yield
    // nothing, so captured inventory lines can still populate the HSN summary.
    private static String firstItemValue(List<ParsedVoucherItem> items,
                                         Function<ParsedVoucherItem, String> getter) {
        return items.stream()
                .map(getter)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private static BigDecimal sumItemQuantities(List<ParsedVoucherItem> items) {
        BigDecimal total = items.stream()
                .map(ParsedVoucherItem::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return items.stream().anyMatch(i -> i.getQuantity() != null) ? total : null;
    }

    // Ledger-scope fill-ins are baked into the view here, so the classifier's
    // voucher-scope resolve still sees them via the built VoucherView fields.
    private Optional<String> resolveText(Workflow workflow, String voucherKey, List<ParsedVoucherEntry> entries,
                                         Gstr1FillInAttribute attribute) {
        Optional<String> voucherScope = fillInService
                .resolve(workflow, voucherKey, null, attribute);
        if (voucherScope.isPresent()) {
            return voucherScope;
        }
        for (ParsedVoucherEntry e : entries) {
            if (e.getLedgerName() == null) {
                continue;
            }
            Optional<String> value = fillInService.resolve(
                    workflow, voucherKey, e.getLedgerName(), attribute);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private static BigDecimal parseAmount(String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static GstHead head(String ledgerName, Map<String, TaxLedgerMapping> taxLedgers) {
        TaxLedgerMapping mapping = taxLedgers.get(key(ledgerName));
        if (mapping == null || mapping.getRole() == null || !mapping.getRole().isVoucherTax()) {
            return null;
        }
        return mapping.getHead();
    }

    private static String key(String ledgerName) {
        return ledgerName == null ? "" : ledgerName.trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal totalValue(ParsedVoucher voucher) {
        if (voucher.getTotalDebit() != null && voucher.getTotalDebit().compareTo(BigDecimal.ZERO) != 0) {
            return voucher.getTotalDebit().abs();
        }
        if (voucher.getTotalCredit() != null) {
            return voucher.getTotalCredit().abs();
        }
        return BigDecimal.ZERO;
    }

    private String supplierStateCode(UUID organizationId) {
        Optional<Organization> org = organizationRepository.findById(organizationId);
        if (org.isEmpty() || org.get().getGstin() == null) {
            return null;
        }
        String gstin = org.get().getGstin().trim();
        return gstin.length() >= 2 ? gstin.substring(0, 2) : null;
    }

    private Map<String, TaxLedgerMapping> taxLedgersByLedgerKey(UUID organizationId) {
        Map<String, TaxLedgerMapping> byKey = new HashMap<>();
        for (TaxLedgerMapping m : taxLedgerMappingRepository.findByOrganizationId(organizationId)) {
            byKey.put(key(m.getLedgerName()), m);
        }
        return byKey;
    }

    private Map<String, UploadLedgerSnapshot> snapshotsByLedgerKey(UUID uploadJobId) {
        Map<String, UploadLedgerSnapshot> byKey = new HashMap<>();
        for (UploadLedgerSnapshot s : snapshotRepository.findByUploadJobId(uploadJobId)) {
            byKey.put(key(s.getLedgerName()), s);
        }
        return byKey;
    }
}
