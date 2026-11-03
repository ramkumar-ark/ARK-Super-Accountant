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
import java.util.List;
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

    static final Set<String> TYPED_INPUT_RULES =
            Set.of("GSTIN_PRESENCE", "HSN_SAC_CODE", "TDS_SECTION_MAPPING", "GST_APPLICABILITY");

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
        if (TYPED_INPUT_RULES.contains(f.getRuleCode())) {
            requireTypedValue(f.getRuleCode(), value);
            return;
        }
        if (!"MISMATCH_DETECTION".equals(f.getRuleCode()) || f.getMismatchType() == null) {
            throw badRequest("This finding type cannot be resolved by overriding a master value.");
        }
        switch (f.getMismatchType()) {
            case NAME_MISMATCH, PARENT_GROUP_MISMATCH -> { }
            case GST_APPLICABILITY_MISMATCH, TDS_APPLICABILITY_MISMATCH -> parseFlag(f.getActualValue());
            case MISSING_IN_CONFIGURATION -> findSnapshot(f);
            default -> throw badRequest("This finding type cannot be resolved by overriding a master value.");
        }
    }

    /**
     * Applies the finding's proposal to the master. Throws {@link FindingOverrideException} on any
     * problem; the caller's transaction must roll back.
     */
    public void apply(ValidationFinding f, UUID orgId, String approver) {
        String submitter = f.getSubmittedBy();
        if (TYPED_INPUT_RULES.contains(f.getRuleCode())) {
            String value = f.getProposedValue();
            requireTypedValue(f.getRuleCode(), value);
            PreconfiguredMaster master = findMaster(orgId, f.getLedgerName());
            switch (f.getRuleCode()) {
                case "GSTIN_PRESENCE" -> change(master, f, "gstin", master.getGstin(), value.trim(),
                        approver, submitter, () -> master.setGstin(value.trim()));
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
        if (!"MISMATCH_DETECTION".equals(f.getRuleCode()) || f.getMismatchType() == null) {
            throw badRequest("This finding type cannot be resolved by overriding a master value.");
        }
        switch (f.getMismatchType()) {
            case NAME_MISMATCH -> {
                // ledgerName holds the Tally (new) name; the master is located by its current name (expectedValue).
                PreconfiguredMaster master = masterRepository.findByOrganizationIdAndActiveTrue(orgId).stream()
                        .filter(m -> Objects.equals(m.getLedgerName(), f.getExpectedValue()))
                        .findFirst()
                        .orElseThrow(() -> new FindingOverrideException(HttpStatus.CONFLICT,
                                "Master changed since submission; reject and resubmit."));
                String newName = f.getActualValue();
                change(master, f, "ledger_name", master.getLedgerName(), newName,
                        approver, submitter, () -> master.setLedgerName(newName));
            }
            case PARENT_GROUP_MISMATCH -> {
                PreconfiguredMaster master = findMaster(orgId, f.getLedgerName());
                String v = f.getActualValue();
                change(master, f, "expected_parent_group", master.getExpectedParentGroup(), v,
                        approver, submitter, () -> master.setExpectedParentGroup(v));
            }
            case GST_APPLICABILITY_MISMATCH -> {
                Boolean v = parseFlag(f.getActualValue());
                PreconfiguredMaster master = findMaster(orgId, f.getLedgerName());
                change(master, f, "expected_gst_applicable", str(master.getExpectedGstApplicable()), str(v),
                        approver, submitter, () -> master.setExpectedGstApplicable(v));
            }
            case TDS_APPLICABILITY_MISMATCH -> {
                Boolean v = parseFlag(f.getActualValue());
                PreconfiguredMaster master = findMaster(orgId, f.getLedgerName());
                change(master, f, "expected_tds_applicable", str(master.getExpectedTdsApplicable()), str(v),
                        approver, submitter, () -> master.setExpectedTdsApplicable(v));
            }
            case MISSING_IN_CONFIGURATION -> createFromSnapshot(f, orgId, approver, submitter);
            default -> throw badRequest("This finding type cannot be resolved by overriding a master value.");
        }
    }

    // --- helpers ---

    private void createFromSnapshot(ValidationFinding f, UUID orgId, String approver, String submitter) {
        UploadLedgerSnapshot s = findSnapshot(f);
        boolean exists = masterRepository.findByOrganizationIdAndActiveTrue(orgId).stream()
                .anyMatch(m -> LedgerNames.normalize(m.getLedgerName()).equals(LedgerNames.normalize(f.getLedgerName())));
        if (exists) {
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
        m.setTdsSection(s.getTdsSection());
        m.setHsnSacCode(s.getHsnSacCode());
        m.setGstApplicabilityType(s.getGstApplicabilityType());
        m.setUpdatedAt(Instant.now());
        PreconfiguredMaster saved = masterRepository.save(m);

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

    private PreconfiguredMaster findMaster(UUID orgId, String ledgerName) {
        String key = LedgerNames.normalize(ledgerName);
        return masterRepository.findByOrganizationIdAndActiveTrue(orgId).stream()
                .filter(m -> LedgerNames.normalize(m.getLedgerName()).equals(key))
                .findFirst()
                .orElseThrow(() -> new FindingOverrideException(HttpStatus.CONFLICT,
                        "Master changed since submission; reject and resubmit."));
    }

    private UploadLedgerSnapshot findSnapshot(ValidationFinding f) {
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
            case "GSTIN_PRESENCE" -> requireMatch(GSTIN_PATTERN, v,
                    "GSTIN must be 15 characters in format: 22AAAAA0000A1Z5");
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
