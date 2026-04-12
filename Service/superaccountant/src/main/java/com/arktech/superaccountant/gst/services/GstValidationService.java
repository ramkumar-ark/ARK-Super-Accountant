package com.arktech.superaccountant.gst.services;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.arktech.superaccountant.gst.models.GstErrorType;
import com.arktech.superaccountant.gst.models.GstValidationError;
import com.arktech.superaccountant.gst.models.GstValidationResult;
import com.arktech.superaccountant.tally.models.GstRateDetail;
import com.arktech.superaccountant.tally.models.LedgerEntry;
import com.arktech.superaccountant.tally.models.TallyMessage;
import com.arktech.superaccountant.tally.models.Voucher;

@Service
public class GstValidationService {

    private static final Pattern GST_LEDGER_PATTERN = Pattern.compile(
            "(?i).*(CGST|SGST|IGST).*");

    private static final Pattern GSTIN_PATTERN = Pattern.compile(
            "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");

    private static final double TOLERANCE = 1.0;

    public List<GstValidationResult> validate(TallyMessage tallyMessage) {
        List<GstValidationResult> results = new ArrayList<>();

        if (tallyMessage.getTallymessage() == null) {
            return results;
        }

        for (Voucher voucher : tallyMessage.getTallymessage()) {
            GstValidationResult result = new GstValidationResult(
                    voucher.getVouchernumber(),
                    voucher.getVouchertypename(),
                    voucher.getDate(),
                    voucher.getPartyledgername()
            );

            result.getErrors().addAll(checkGstNotCharged(voucher));
            result.getErrors().addAll(checkIncorrectGstAmount(voucher));
            result.getErrors().addAll(checkMissingSupplierGstin(voucher));
            result.getErrors().addAll(checkIncorrectGstType(voucher));
            result.getErrors().addAll(checkRcmMixedWithTaxable(voucher));
            result.getErrors().addAll(checkIncorrectGstLedger(voucher));

            results.add(result);
        }

        return results;
    }

    // --- Validation Rule 1: GST Not Charged ---

    List<GstValidationError> checkGstNotCharged(Voucher voucher) {
        List<GstValidationError> errors = new ArrayList<>();
        List<LedgerEntry> taxableEntries = getTaxableLedgerEntries(voucher);
        List<LedgerEntry> gstEntries = getGstLedgerEntries(voucher);

        if (taxableEntries.isEmpty()) {
            return errors;
        }

        boolean hasNonZeroGst = gstEntries.stream()
                .anyMatch(e -> parseAmount(e.getAmount()) != 0.0);

        if (!hasNonZeroGst) {
            for (LedgerEntry taxable : taxableEntries) {
                errors.add(new GstValidationError(
                        GstErrorType.GST_NOT_CHARGED,
                        "Taxable ledger '" + taxable.getLedgername() + "' has no corresponding GST entries",
                        taxable.getLedgername(),
                        "CGST/SGST or IGST entries with non-zero amounts",
                        "No GST ledger entries found"
                ));
            }
        }

        return errors;
    }

    // --- Validation Rule 2: Incorrect GST Amount ---

    List<GstValidationError> checkIncorrectGstAmount(Voucher voucher) {
        List<GstValidationError> errors = new ArrayList<>();
        List<LedgerEntry> taxableEntries = getTaxableLedgerEntries(voucher);
        List<LedgerEntry> gstEntries = getGstLedgerEntries(voucher);

        if (taxableEntries.isEmpty() || gstEntries.isEmpty()) {
            return errors;
        }

        for (LedgerEntry taxable : taxableEntries) {
            if (taxable.getRatedetails() == null || taxable.getRatedetails().isEmpty()) {
                continue;
            }

            double taxableAmount = Math.abs(parseAmount(taxable.getAmount()));

            for (GstRateDetail rateDetail : taxable.getRatedetails()) {
                double rate = parseAmount(rateDetail.getGstrate());
                if (rate == 0.0) {
                    continue;
                }

                String dutyHead = rateDetail.getGstratedutyhead();
                if (dutyHead == null || dutyHead.isBlank()) {
                    continue;
                }

                double expectedGst = taxableAmount * rate / 100.0;

                // Find matching GST ledger entry for this duty head
                for (LedgerEntry gstEntry : gstEntries) {
                    String component = getGstComponent(gstEntry.getLedgername());
                    if (component != null && dutyHead.toUpperCase().contains(component)) {
                        double actualGst = Math.abs(parseAmount(gstEntry.getAmount()));
                        if (Math.abs(expectedGst - actualGst) > TOLERANCE) {
                            errors.add(new GstValidationError(
                                    GstErrorType.INCORRECT_GST_AMOUNT,
                                    "GST amount mismatch for " + dutyHead + " on '" + taxable.getLedgername() + "'",
                                    gstEntry.getLedgername(),
                                    String.format("%.2f (%.2f * %.2f%%)", expectedGst, taxableAmount, rate),
                                    String.format("%.2f", actualGst)
                            ));
                        }
                    }
                }
            }
        }

        return errors;
    }

    // --- Validation Rule 3: Missing Supplier GSTIN ---

    List<GstValidationError> checkMissingSupplierGstin(Voucher voucher) {
        List<GstValidationError> errors = new ArrayList<>();
        String voucherType = voucher.getVouchertypename();

        if (voucherType == null) {
            return errors;
        }

        boolean isPurchaseOrPayment = voucherType.equalsIgnoreCase("Purchase")
                || voucherType.equalsIgnoreCase("Payment")
                || voucherType.equalsIgnoreCase("Debit Note");

        if (!isPurchaseOrPayment) {
            return errors;
        }

        String regType = voucher.getGstregistrationtype();
        if (regType == null || regType.isBlank()) {
            return errors;
        }

        boolean isRegistered = regType.equalsIgnoreCase("Regular")
                || regType.equalsIgnoreCase("Composition")
                || regType.toLowerCase().contains("regular");

        if (!isRegistered) {
            return errors;
        }

        String gstin = voucher.getPartygstin();
        if (gstin == null || gstin.isBlank()) {
            errors.add(new GstValidationError(
                    GstErrorType.MISSING_SUPPLIER_GSTIN,
                    "GSTIN missing for registered supplier '" + voucher.getPartyledgername() + "'",
                    voucher.getPartyledgername(),
                    "Valid 15-character GSTIN",
                    "Empty/missing"
            ));
        } else if (!GSTIN_PATTERN.matcher(gstin.trim()).matches()) {
            errors.add(new GstValidationError(
                    GstErrorType.MISSING_SUPPLIER_GSTIN,
                    "Invalid GSTIN format for supplier '" + voucher.getPartyledgername() + "'",
                    voucher.getPartyledgername(),
                    "15-char GSTIN matching format: 22AAAAA0000A1Z5",
                    gstin
            ));
        }

        return errors;
    }

    // --- Validation Rule 4: Incorrect GST Type (SGST vs IGST) ---

    List<GstValidationError> checkIncorrectGstType(Voucher voucher) {
        List<GstValidationError> errors = new ArrayList<>();
        List<LedgerEntry> gstEntries = getGstLedgerEntries(voucher);

        if (gstEntries.isEmpty()) {
            return errors;
        }

        Boolean interState = isInterState(voucher);
        if (interState == null) {
            return errors; // Can't determine, skip
        }

        boolean hasCgstSgst = gstEntries.stream()
                .anyMatch(e -> {
                    String comp = getGstComponent(e.getLedgername());
                    return "CGST".equals(comp) || "SGST".equals(comp);
                });

        boolean hasIgst = gstEntries.stream()
                .anyMatch(e -> "IGST".equals(getGstComponent(e.getLedgername())));

        if (interState) {
            // Inter-state: should have IGST only
            if (hasCgstSgst) {
                errors.add(new GstValidationError(
                        GstErrorType.INCORRECT_GST_TYPE,
                        "CGST/SGST charged on inter-state transaction (place of supply: "
                                + voucher.getPlaceofsupply() + ", company state: " + voucher.getCmpgststate() + ")",
                        null,
                        "IGST only",
                        "CGST/SGST found"
                ));
            }
            if (!hasIgst) {
                errors.add(new GstValidationError(
                        GstErrorType.INCORRECT_GST_TYPE,
                        "IGST not found for inter-state transaction (place of supply: "
                                + voucher.getPlaceofsupply() + ", company state: " + voucher.getCmpgststate() + ")",
                        null,
                        "IGST entry",
                        "No IGST found"
                ));
            }
        } else {
            // Intra-state: should have CGST + SGST only
            if (hasIgst) {
                errors.add(new GstValidationError(
                        GstErrorType.INCORRECT_GST_TYPE,
                        "IGST charged on intra-state transaction (place of supply: "
                                + voucher.getPlaceofsupply() + ", company state: " + voucher.getCmpgststate() + ")",
                        null,
                        "CGST + SGST only",
                        "IGST found"
                ));
            }
            if (!hasCgstSgst) {
                errors.add(new GstValidationError(
                        GstErrorType.INCORRECT_GST_TYPE,
                        "CGST/SGST not found for intra-state transaction (place of supply: "
                                + voucher.getPlaceofsupply() + ", company state: " + voucher.getCmpgststate() + ")",
                        null,
                        "CGST + SGST entries",
                        "No CGST/SGST found"
                ));
            }
        }

        return errors;
    }

    // --- Validation Rule 5: RCM Mixed with Taxable ---

    List<GstValidationError> checkRcmMixedWithTaxable(Voucher voucher) {
        List<GstValidationError> errors = new ArrayList<>();

        if (!voucher.isIsreversechargeapplicable()) {
            return errors;
        }

        List<LedgerEntry> allEntries = voucher.getAllLedgerEntriesCombined();
        List<LedgerEntry> nonGstTaxableEntries = allEntries.stream()
                .filter(e -> !isGstLedger(e.getLedgername()))
                .filter(e -> {
                    String taxability = e.getGstovrdntaxability();
                    String nature = e.getGstovrdnnature();
                    boolean isTaxable = "Taxable".equalsIgnoreCase(taxability)
                            || (nature != null && nature.toLowerCase().contains("taxable"));
                    return isTaxable;
                })
                .collect(Collectors.toList());

        // In an RCM voucher, if there are multiple taxable expense entries,
        // some might be regular taxable (non-RCM). Flag this mixture.
        if (nonGstTaxableEntries.size() > 1) {
            errors.add(new GstValidationError(
                    GstErrorType.RCM_MIXED_WITH_TAXABLE,
                    "Reverse charge voucher contains " + nonGstTaxableEntries.size()
                            + " taxable expense entries - verify all are RCM applicable",
                    null,
                    "Only RCM-related entries",
                    nonGstTaxableEntries.stream()
                            .map(LedgerEntry::getLedgername)
                            .collect(Collectors.joining(", "))
            ));
        }

        return errors;
    }

    // --- Validation Rule 6: Incorrect GST Ledger ---

    List<GstValidationError> checkIncorrectGstLedger(Voucher voucher) {
        List<GstValidationError> errors = new ArrayList<>();
        String voucherType = voucher.getVouchertypename();

        if (voucherType == null) {
            return errors;
        }

        List<LedgerEntry> gstEntries = getGstLedgerEntries(voucher);
        if (gstEntries.isEmpty()) {
            return errors;
        }

        boolean isPurchase = voucherType.equalsIgnoreCase("Purchase")
                || voucherType.equalsIgnoreCase("Debit Note");
        boolean isSales = voucherType.equalsIgnoreCase("Sales")
                || voucherType.equalsIgnoreCase("Credit Note");

        if (!isPurchase && !isSales) {
            return errors;
        }

        for (LedgerEntry gstEntry : gstEntries) {
            String name = gstEntry.getLedgername();
            if (name == null) continue;

            boolean isInput = isInputGstLedger(name);
            boolean isOutput = isOutputGstLedger(name);

            if (isPurchase && isOutput) {
                errors.add(new GstValidationError(
                        GstErrorType.INCORRECT_GST_LEDGER,
                        "Output GST ledger used in Purchase voucher",
                        name,
                        "Input GST ledger (e.g., Input CGST)",
                        name
                ));
            } else if (isSales && isInput) {
                errors.add(new GstValidationError(
                        GstErrorType.INCORRECT_GST_LEDGER,
                        "Input GST ledger used in Sales voucher",
                        name,
                        "Output GST ledger (e.g., Output CGST)",
                        name
                ));
            }
        }

        return errors;
    }

    // --- Helper Methods ---

    boolean isGstLedger(String ledgerName) {
        if (ledgerName == null) return false;
        return GST_LEDGER_PATTERN.matcher(ledgerName).matches();
    }

    boolean isInputGstLedger(String ledgerName) {
        if (ledgerName == null) return false;
        return ledgerName.toLowerCase().contains("input") && isGstLedger(ledgerName);
    }

    boolean isOutputGstLedger(String ledgerName) {
        if (ledgerName == null) return false;
        return ledgerName.toLowerCase().contains("output") && isGstLedger(ledgerName);
    }

    String getGstComponent(String ledgerName) {
        if (ledgerName == null) return null;
        String upper = ledgerName.toUpperCase();
        if (upper.contains("IGST")) return "IGST";
        if (upper.contains("CGST")) return "CGST";
        if (upper.contains("SGST")) return "SGST";
        return null;
    }

    Boolean isInterState(Voucher voucher) {
        String placeOfSupply = voucher.getPlaceofsupply();
        String companyState = voucher.getCmpgststate();

        if (placeOfSupply == null || placeOfSupply.isBlank()
                || companyState == null || companyState.isBlank()) {
            return null;
        }

        return !placeOfSupply.trim().equalsIgnoreCase(companyState.trim());
    }

    List<LedgerEntry> getGstLedgerEntries(Voucher voucher) {
        return voucher.getAllLedgerEntriesCombined().stream()
                .filter(e -> isGstLedger(e.getLedgername()))
                .collect(Collectors.toList());
    }

    List<LedgerEntry> getTaxableLedgerEntries(Voucher voucher) {
        return voucher.getAllLedgerEntriesCombined().stream()
                .filter(e -> {
                    String taxability = e.getGstovrdntaxability();
                    String nature = e.getGstovrdnnature();
                    return "Taxable".equalsIgnoreCase(taxability)
                            || (nature != null && nature.toLowerCase().contains("taxable"));
                })
                .collect(Collectors.toList());
    }

    private double parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(amount.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
