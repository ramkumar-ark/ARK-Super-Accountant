package com.arktech.superaccountant.masters.services;

import com.arktech.superaccountant.masters.models.*;
import com.arktech.superaccountant.masters.repository.MasterEditLogRepository;
import com.arktech.superaccountant.masters.repository.PreconfiguredMasterRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.masters.util.LedgerNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Writes an approved finding override back to {@code preconfigured_masters} and audits it.
 * {@link #validate} runs at submission time; {@link #apply} runs at approval time, inside the
 * caller's transaction, so any exception rolls back both the master write and the status change.
 */
@Service
public class FindingOverrideMasterService {

    private static final Pattern GSTIN_PATTERN =
            Pattern.compile("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}");
    private static final Pattern HSN_SAC_PATTERN = Pattern.compile("\\d{4,8}");
    private static final Pattern TDS_SECTION_PATTERN =
            Pattern.compile("NOT_SUBJECT|194C|194J_A|194J_B|194H|194I|194Q|194A|194B|194D|194M|OTHER");

    /** Value of a GSTIN_PRESENCE override that marks the ledger's GST as not applicable instead of entering a GSTIN. */
    public static final String GST_NOT_APPLICABLE = "NOT_APPLICABLE";

    static final Set<String> TYPED_INPUT_RULES =
            Set.of("GSTIN_PRESENCE", "HSN_SAC_CODE", "TDS_SECTION_MAPPING", "GST_APPLICABILITY");

    private static final Set<MismatchType> DERIVABLE_MISMATCH_TYPES = EnumSet.of(
            MismatchType.NAME_MISMATCH, MismatchType.PARENT_GROUP_MISMATCH,
            MismatchType.GST_APPLICABILITY_MISMATCH, MismatchType.TDS_APPLICABILITY_MISMATCH,
            MismatchType.MISSING_IN_CONFIGURATION);

    /**
     * The rule a bulk value applies to, or null when the value is not an allowed bulk value. Bulk takes one chosen TDS
     * section (or NOT_SUBJECT) for TDS findings, or NOT_APPLICABLE for GSTIN findings; never a GSTIN, HSN/SAC or GST type.
     */
    public static String bulkRuleForValue(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (GST_NOT_APPLICABLE.equals(v)) {
            return "GSTIN_PRESENCE";
        }
        return TDS_SECTION_PATTERN.matcher(v).matches() ? "TDS_SECTION_MAPPING" : null;
    }

    /** True when the corrected master value comes from the finding itself, so no typed value is needed. */
    public static boolean isDerivable(ValidationFinding f) {
        if (!isOverridableMismatch(f)) {
            return false;
        }
        return switch (f.getMismatchType()) {
            case GST_APPLICABILITY_MISMATCH, TDS_APPLICABILITY_MISMATCH ->
                    "true".equals(f.getActualValue()) || "false".equals(f.getActualValue());
            default -> true;
        };
    }

    private static boolean isOverridableMismatch(ValidationFinding f) {
        return "MISMATCH_DETECTION".equals(f.getRuleCode()) && DERIVABLE_MISMATCH_TYPES.contains(f.getMismatchType());
    }

    @Autowired
    private PreconfiguredMasterRepository masterRepository;

    @Autowired
    private UploadLedgerSnapshotRepository snapshotRepository;

    @Autowired
    private MasterEditLogRepository editLogRepository;

    /**
     * Submission-time checks, so a proposal that can never be applied is rejected immediately.
     * Does not write anything.
     */
    public void validate(ValidationFinding f, String value, UUID orgId) {
        validate(f, value, orgId, null);
    }

    /** As {@link #validate(ValidationFinding, String, UUID)}, reading uploaded ledgers from a batch-loaded map when given. */
    public void validate(ValidationFinding f, String value, UUID orgId, Map<String, UploadLedgerSnapshot> snapshots) {
        if (TYPED_INPUT_RULES.contains(f.getRuleCode())) {
            requireTypedValue(f.getRuleCode(), value);
            return;
        }
        if (!isOverridableMismatch(f)) {
            throw badRequest("This finding type cannot be resolved by overriding a master value.");
        }
        switch (f.getMismatchType()) {
            case NAME_MISMATCH, PARENT_GROUP_MISMATCH -> { }
            case GST_APPLICABILITY_MISMATCH, TDS_APPLICABILITY_MISMATCH -> parseFlag(f.getActualValue());
            case MISSING_IN_CONFIGURATION -> findSnapshot(f, snapshots);
            default -> throw badRequest("This finding type cannot be resolved by overriding a master value.");
        }
    }

    /**
     * Records an override proposal: validates it, then moves the finding to pending review (ACKNOWLEDGED),
     * clearing any earlier review. Writes nothing to the master.
     */
    public void submit(ValidationFinding f, String value, String note, UUID orgId, String submitter, Instant now) {
        submit(f, value, note, orgId, submitter, now, null);
    }

    public void submit(ValidationFinding f, String value, String note, UUID orgId, String submitter, Instant now,
                       Map<String, UploadLedgerSnapshot> snapshots) {
        validate(f, value, orgId, snapshots);
        f.setResolveStatus(ResolveStatus.ACKNOWLEDGED);
        f.setResolveNote(note);
        f.setProposedValue(value);
        f.setSubmittedBy(submitter);
        f.setSubmittedAt(now);
        f.setReviewedBy(null);
        f.setReviewedAt(null);
        f.setReviewNote(null);
        f.setResolvedBy(null);
        f.setResolvedAt(null);
    }

    /**
     * Applies the finding's proposal to the master. Throws {@link FindingOverrideException} on any
     * problem; the caller's transaction must roll back.
     */
    public void apply(ValidationFinding f, UUID orgId, String approver) {
        apply(f, orgId, approver, activeMastersByName(orgId));
    }

    /**
     * The organization's active masters keyed by normalized ledger name, loaded once for a batch of approvals.
     * {@link #apply(ValidationFinding, UUID, String, Map)} keeps it current as masters are renamed or created.
     */
    public Map<String, PreconfiguredMaster> activeMastersByName(UUID orgId) {
        Map<String, PreconfiguredMaster> masters = new HashMap<>();
        for (PreconfiguredMaster m : masterRepository.findByOrganizationIdAndActiveTrue(orgId)) {
            masters.putIfAbsent(LedgerNames.normalize(m.getLedgerName()), m);
        }
        return masters;
    }

    public void apply(ValidationFinding f, UUID orgId, String approver, Map<String, PreconfiguredMaster> masters) {
        apply(f, orgId, approver, masters, null);
    }

    /**
     * The upload's ledgers keyed by normalized name, loaded once for a batch, so each missing-in-configuration
     * finding does not run its own lookup. The first ledger per name wins, as in the single-finding lookup.
     */
    public Map<String, UploadLedgerSnapshot> snapshotsByName(UUID jobId) {
        Map<String, UploadLedgerSnapshot> byName = new HashMap<>();
        for (UploadLedgerSnapshot s : snapshotRepository.findByUploadJobId(jobId)) {
            byName.putIfAbsent(LedgerNames.normalize(s.getLedgerName()), s);
        }
        return byName;
    }

    public void apply(ValidationFinding f, UUID orgId, String approver, Map<String, PreconfiguredMaster> masters,
                      Map<String, UploadLedgerSnapshot> snapshots) {
        String submitter = f.getSubmittedBy();
        if (TYPED_INPUT_RULES.contains(f.getRuleCode())) {
            String value = f.getProposedValue();
            requireTypedValue(f.getRuleCode(), value);
            PreconfiguredMaster master = findMaster(masters, f.getLedgerName());
            switch (f.getRuleCode()) {
                case "GSTIN_PRESENCE" -> {
                    if (GST_NOT_APPLICABLE.equals(value.trim())) {
                        change(master, f, "gst_applicability_type",
                                master.getGstApplicabilityType() == null ? null : master.getGstApplicabilityType().name(),
                                GstApplicabilityType.NOT_APPLICABLE.name(), approver, submitter,
                                () -> master.setGstApplicabilityType(GstApplicabilityType.NOT_APPLICABLE));
                    } else {
                        change(master, f, "gstin", master.getGstin(), value.trim(),
                                approver, submitter, () -> master.setGstin(value.trim()));
                    }
                }
                case "HSN_SAC_CODE" -> change(master, f, "hsn_sac_code", master.getHsnSacCode(), value.trim(),
                        approver, submitter, () -> master.setHsnSacCode(value.trim()));
                case "TDS_SECTION_MAPPING" -> change(master, f, "tds_section", master.getTdsSection(), value.trim(),
                        approver, submitter, () -> master.setTdsSection(value.trim()));
                default -> {
                    GstApplicabilityType type = parseGstType(value);
                    change(master, f, "gst_applicability_type",
                            master.getGstApplicabilityType() == null ? null : master.getGstApplicabilityType().name(),
                            type.name(), approver, submitter, () -> master.setGstApplicabilityType(type));
                }
            }
            return;
        }
        if (!isOverridableMismatch(f)) {
            throw badRequest("This finding type cannot be resolved by overriding a master value.");
        }
        switch (f.getMismatchType()) {
            case NAME_MISMATCH -> {
                // ledgerName holds the Tally (new) name; the master is located by its current name (expectedValue).
                PreconfiguredMaster master = masters.get(LedgerNames.normalize(f.getExpectedValue()));
                if (master == null || !Objects.equals(master.getLedgerName(), f.getExpectedValue())) {
                    throw new FindingOverrideException(HttpStatus.CONFLICT,
                            "Master changed since submission; reject and resubmit.");
                }
                String oldKey = LedgerNames.normalize(master.getLedgerName());
                String newName = f.getActualValue();
                change(master, f, "ledger_name", master.getLedgerName(), newName,
                        approver, submitter, () -> master.setLedgerName(newName));
                masters.remove(oldKey, master);
                masters.putIfAbsent(LedgerNames.normalize(newName), master);
            }
            case PARENT_GROUP_MISMATCH -> {
                PreconfiguredMaster master = findMaster(masters, f.getLedgerName());
                String v = f.getActualValue();
                change(master, f, "expected_parent_group", master.getExpectedParentGroup(), v,
                        approver, submitter, () -> master.setExpectedParentGroup(v));
            }
            case GST_APPLICABILITY_MISMATCH -> {
                Boolean v = parseFlag(f.getActualValue());
                PreconfiguredMaster master = findMaster(masters, f.getLedgerName());
                change(master, f, "expected_gst_applicable", str(master.getExpectedGstApplicable()), str(v),
                        approver, submitter, () -> master.setExpectedGstApplicable(v));
            }
            case TDS_APPLICABILITY_MISMATCH -> {
                Boolean v = parseFlag(f.getActualValue());
                PreconfiguredMaster master = findMaster(masters, f.getLedgerName());
                change(master, f, "expected_tds_applicable", str(master.getExpectedTdsApplicable()), str(v),
                        approver, submitter, () -> master.setExpectedTdsApplicable(v));
            }
            case MISSING_IN_CONFIGURATION -> createFromSnapshot(f, orgId, approver, submitter, masters, snapshots);
            default -> throw badRequest("This finding type cannot be resolved by overriding a master value.");
        }
    }

    // --- helpers ---

    private void createFromSnapshot(ValidationFinding f, UUID orgId, String approver, String submitter,
                                    Map<String, PreconfiguredMaster> masters,
                                    Map<String, UploadLedgerSnapshot> snapshots) {
        UploadLedgerSnapshot s = findSnapshot(f, snapshots);
        if (masters.containsKey(LedgerNames.normalize(f.getLedgerName()))) {
            throw new FindingOverrideException(HttpStatus.CONFLICT,
                    "Master changed since submission; reject and resubmit.");
        }
        PreconfiguredMaster m = new PreconfiguredMaster();
        m.setOrganizationId(orgId);
        m.setLedgerName(s.getLedgerName());
        m.setCategory(f.getCategory());
        m.setExpectedParentGroup(s.getParentGroup());
        m.setExpectedGstApplicable(s.getGstApplicable());
        m.setExpectedTdsApplicable(s.getTdsApplicable());
        m.setGstin(s.getGstin());
        m.setGstRegistrationType(s.getGstRegistrationType());
        m.setGroupChain(s.getGroupChain());
        m.setTdsSection(s.getTdsSection());
        m.setHsnSacCode(s.getHsnSacCode());
        m.setGstApplicabilityType(s.getGstApplicabilityType());
        m.setUpdatedAt(Instant.now());
        PreconfiguredMaster saved = masterRepository.save(m);
        masters.put(LedgerNames.normalize(f.getLedgerName()), saved);

        List<MasterEditLog> logs = new ArrayList<>();
        addCreateLog(logs, saved, f, "ledger_name", saved.getLedgerName(), approver, submitter);
        addCreateLog(logs, saved, f, "expected_parent_group", saved.getExpectedParentGroup(), approver, submitter);
        addCreateLog(logs, saved, f, "expected_gst_applicable", str(saved.getExpectedGstApplicable()), approver, submitter);
        addCreateLog(logs, saved, f, "expected_tds_applicable", str(saved.getExpectedTdsApplicable()), approver, submitter);
        addCreateLog(logs, saved, f, "gstin", saved.getGstin(), approver, submitter);
        addCreateLog(logs, saved, f, "tds_section", saved.getTdsSection(), approver, submitter);
        addCreateLog(logs, saved, f, "hsn_sac_code", saved.getHsnSacCode(), approver, submitter);
        addCreateLog(logs, saved, f, "gst_applicability_type",
                saved.getGstApplicabilityType() == null ? null : saved.getGstApplicabilityType().name(), approver, submitter);
        editLogRepository.saveAll(logs);
    }

    private void addCreateLog(List<MasterEditLog> logs, PreconfiguredMaster m, ValidationFinding f,
                              String field, String newValue, String approver, String submitter) {
        if (newValue != null) {
            logs.add(log(m, f, field, null, newValue, approver, submitter));
        }
    }

    private void change(PreconfiguredMaster master, ValidationFinding f, String field, String oldValue,
                        String newValue, String approver, String submitter, Runnable write) {
        if (Objects.equals(oldValue, newValue)) {
            return; // nothing to change, nothing to log
        }
        write.run();
        master.setUpdatedAt(Instant.now());
        masterRepository.save(master);
        editLogRepository.save(log(master, f, field, oldValue, newValue, approver, submitter));
    }

    private MasterEditLog log(PreconfiguredMaster m, ValidationFinding f, String field, String oldValue,
                              String newValue, String approver, String submitter) {
        MasterEditLog l = new MasterEditLog();
        l.setMasterId(m.getId());
        l.setLedgerName(m.getLedgerName());
        l.setFieldName(field);
        l.setOldValue(oldValue);
        l.setNewValue(newValue);
        l.setEditedBy(approver);
        l.setSubmittedBy(submitter);
        l.setSource(MasterEditLog.SOURCE_FINDING_OVERRIDE);
        l.setFindingId(f.getId());
        return l;
    }

    private PreconfiguredMaster findMaster(Map<String, PreconfiguredMaster> masters, String ledgerName) {
        PreconfiguredMaster master = masters.get(LedgerNames.normalize(ledgerName));
        if (master == null) {
            throw new FindingOverrideException(HttpStatus.CONFLICT,
                    "Master changed since submission; reject and resubmit.");
        }
        return master;
    }

    private UploadLedgerSnapshot findSnapshot(ValidationFinding f, Map<String, UploadLedgerSnapshot> snapshots) {
        if (snapshots != null) {
            UploadLedgerSnapshot cached = snapshots.get(LedgerNames.normalize(f.getLedgerName()));
            if (cached == null) {
                throw new FindingOverrideException(HttpStatus.CONFLICT,
                        "The uploaded ledger for this finding is no longer available; re-upload the masters file.");
            }
            return cached;
        }
        List<UploadLedgerSnapshot> found = snapshotRepository.findByUploadJobIdAndNormalizedName(
                f.getUploadJobId(), LedgerNames.normalize(f.getLedgerName()));
        if (found.isEmpty()) {
            throw new FindingOverrideException(HttpStatus.CONFLICT,
                    "The uploaded ledger for this finding is no longer available; re-upload the masters file.");
        }
        return found.get(0);
    }

    private void requireTypedValue(String ruleCode, String value) {
        if (value == null || value.isBlank()) {
            throw badRequest("A value is required to resolve this finding.");
        }
        String v = value.trim();
        switch (ruleCode) {
            case "GSTIN_PRESENCE" -> {
                if (!GST_NOT_APPLICABLE.equals(v)) {
                    requireMatch(GSTIN_PATTERN, v, "GSTIN must be 15 characters in format: 22AAAAA0000A1Z5");
                }
            }
            case "HSN_SAC_CODE" -> requireMatch(HSN_SAC_PATTERN, v, "HSN/SAC code must be 4-8 digits");
            case "TDS_SECTION_MAPPING" -> requireMatch(TDS_SECTION_PATTERN, v, "Invalid TDS section code");
            default -> parseGstType(v);
        }
    }

    private static void requireMatch(Pattern pattern, String value, String message) {
        if (!pattern.matcher(value).matches()) {
            throw badRequest(message);
        }
    }

    private static Boolean parseFlag(String value) {
        if ("true".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value)) {
            return Boolean.FALSE;
        }
        throw badRequest("The uploaded ledger has no value for this flag; it cannot be applied to the master.");
    }

    private GstApplicabilityType parseGstType(String value) {
        try {
            return GstApplicabilityType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw badRequest("Invalid GST applicability type. Allowed values: "
                    + Arrays.toString(GstApplicabilityType.values()));
        }
    }

    private static String str(Boolean b) {
        return b == null ? null : b.toString();
    }

    private static FindingOverrideException badRequest(String msg) {
        return new FindingOverrideException(HttpStatus.BAD_REQUEST, msg);
    }
}
