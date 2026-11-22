package com.arktech.superaccountant.tallywriteback.services;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.support.TestDatabase;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeItem;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeSourceType;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeType;
import com.arktech.superaccountant.tallywriteback.models.TallyField;
import com.arktech.superaccountant.tallywriteback.repository.TallyChangeItemRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.seam.SnapshotService;
import com.arktech.superaccountant.workflow.snapshot.FrozenMasterAttribute;
import com.arktech.superaccountant.workflow.snapshot.FrozenMasterAttributeRepository;
import com.arktech.superaccountant.workflow.snapshot.TamperChange;
import com.arktech.superaccountant.workflow.snapshot.TamperChangeKind;
import com.arktech.superaccountant.workflow.snapshot.TamperChangeRepository;
import com.arktech.superaccountant.workflow.snapshot.TamperFinding;
import com.arktech.superaccountant.workflow.snapshot.TamperFindingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over HTTP, not @Transactional: the reconciliation and tamper
 * listeners fire on the masters intake commit, so state must really be
 * committed between steps. Scratch PostgreSQL via the TestDatabase seam.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TallyAppliedPanTamperIT {

    private static final String GUID = "guid-tds-1";
    private static final String LEDGER = "TDS Payable";
    private static final String LEDGER_KEY = "tds payable";
    private static final String BASELINE = "AAAAA1111A";
    private static final String NEW_PAN = "BBBBB2222B";
    private static final String DRIFTED_PAN = "CCCCC3333C";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private TallyChangeItemRepository itemRepository;
    @Autowired
    private WorkflowRepository workflowRepository;
    @Autowired
    private SnapshotService snapshotService;
    @Autowired
    private FrozenMasterAttributeRepository frozenMasterAttributeRepository;
    @Autowired
    private TamperChangeRepository tamperChangeRepository;
    @Autowired
    private TamperFindingRepository tamperFindingRepository;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void appliedPanChange_isSuppressedAndRewritesFrozenRow() throws Exception {
        Organization org = organization();
        String token = token(org);

        UUID job1 = upload(token, mastersFile(BASELINE));
        freezeTdsPayment(org, job1);
        assertThat(frozenPanRows(org)).singleElement()
                .satisfies(row -> assertThat(row.getValue()).isEqualTo(BASELINE));

        TallyChangeItem item = pendingItem(org, BASELINE, NEW_PAN);

        upload(token, mastersFile(NEW_PAN));

        awaitAppliedAndRewritten(item.getId(), org);
        assertThat(tamperChangeRepository.findByOrganizationId(org.getId())).isEmpty();
        assertThat(tamperFindingRepository.findByOrganizationId(org.getId())).isEmpty();
    }

    @Test
    void appliedEmptyPanCorrection_isSuppressedAndClearsFrozenRow() throws Exception {
        Organization org = organization();
        String token = token(org);

        UUID job1 = upload(token, mastersFile(BASELINE));
        freezeTdsPayment(org, job1);
        TallyChangeItem item = pendingItem(org, BASELINE, "");

        upload(token, mastersFile(""));

        awaitAppliedAndCleared(item.getId(), org);
        assertThat(frozenPanRows(org)).singleElement()
                .satisfies(row -> assertThat(row.getValue()).isNull());
        assertThat(tamperChangeRepository.findByOrganizationId(org.getId())).isEmpty();
        assertThat(tamperFindingRepository.findByOrganizationId(org.getId())).isEmpty();
    }

    @Test
    void appliedPanChange_withDriftedFrozenValue_stillRaisesFinding() throws Exception {
        Organization org = organization();
        String token = token(org);

        UUID job1 = upload(token, mastersFile(BASELINE));
        freezeTdsPayment(org, job1);
        FrozenMasterAttribute drifted = frozenPanRows(org).get(0);
        drifted.setValue(DRIFTED_PAN);
        frozenMasterAttributeRepository.saveAndFlush(drifted);

        TallyChangeItem item = pendingItem(org, BASELINE, NEW_PAN);

        upload(token, mastersFile(NEW_PAN));

        // The item really was applied by this upload, yet the frozen row had
        // already drifted away from the item baseline, so suppression must not run.
        assertThat(awaitStatus(item.getId(), TallyChangeStatus.APPLIED).getAppliedUploadJobId())
                .isNotNull();
        TamperChange change = awaitTamperChange(org);
        assertThat(change.getChangeKind()).isEqualTo(TamperChangeKind.MASTER_MODIFIED);
        assertThat(change.getAttributeName()).isEqualTo("pan");
        assertThat(change.getOldValue()).isEqualTo(DRIFTED_PAN);
        assertThat(change.getNewValue()).isEqualTo(NEW_PAN);

        List<TamperFinding> findings = tamperFindingRepository.findByOrganizationId(org.getId());
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.getFindingKey()).isEqualTo("MASTER_MODIFIED|" + LEDGER_KEY + "|pan");
            assertThat(f.getOldValue()).isEqualTo(DRIFTED_PAN);
            assertThat(f.getNewValue()).isEqualTo(NEW_PAN);
        });
        assertThat(frozenPanRows(org)).singleElement()
                .satisfies(row -> assertThat(row.getValue()).isEqualTo(DRIFTED_PAN));
    }

    @Test
    void unappliedPanChange_stillRaisesTamperFinding() throws Exception {
        Organization org = organization();
        String token = token(org);

        UUID job1 = upload(token, mastersFile(BASELINE));
        freezeTdsPayment(org, job1);

        upload(token, mastersFile(NEW_PAN));

        TamperChange change = awaitTamperChange(org);
        assertThat(change.getChangeKind()).isEqualTo(TamperChangeKind.MASTER_MODIFIED);
        assertThat(change.getAttributeName()).isEqualTo("pan");
        assertThat(change.getOldValue()).isEqualTo(BASELINE);
        assertThat(change.getNewValue()).isEqualTo(NEW_PAN);

        List<TamperFinding> findings = tamperFindingRepository.findByOrganizationId(org.getId());
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.getFindingKey()).isEqualTo("MASTER_MODIFIED|" + LEDGER_KEY + "|pan");
            assertThat(f.getOldValue()).isEqualTo(BASELINE);
            assertThat(f.getNewValue()).isEqualTo(NEW_PAN);
        });
    }

    private Organization organization() {
        return organizationRepository.save(new Organization("Tally Pan Tamper Org " + UUID.randomUUID()));
    }

    private String token(Organization org) {
        String username = "tpt_" + UUID.randomUUID().toString().substring(0, 8);
        Role role = roleRepository.findByName(ERole.ROLE_ACCOUNTANT)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_ACCOUNTANT)));
        User user = new User(username, username + "@test.com", "pass");
        user.setRole(role);
        userRepository.save(user);
        return jwtUtils.generateJwtTokenForUser(username, org.getId());
    }

    private void freezeTdsPayment(Organization org, UUID mastersJobId) {
        Workflow w = new Workflow();
        w.setOrganizationId(org.getId());
        w.setType(WorkflowType.TDS_PAYMENT);
        w.setPeriodStart(LocalDate.of(2025, 4, 1));
        w.setPeriodEnd(LocalDate.of(2025, 4, 30));
        w.setPeriodKey("2025-04");
        w.setFinancialYear(2025);
        w.setStage(WorkflowStage.FINALIZED);
        w.setActive(true);
        w.setMastersJobId(mastersJobId);
        w.setCreatedBy("tpt_accountant");
        w = workflowRepository.saveAndFlush(w);
        snapshotService.freeze(w.getId(), org.getId(), "tpt_accountant");
    }

    private List<FrozenMasterAttribute> frozenPanRows(Organization org) {
        return frozenMasterAttributeRepository.findByOrganizationIdAndReleasedAtIsNull(org.getId())
                .stream()
                .filter(r -> LEDGER_KEY.equals(r.getLedgerKey()))
                .filter(r -> "pan".equals(r.getAttribute()))
                .toList();
    }

    private TallyChangeItem pendingItem(Organization org, String baseline, String newValue) {
        TallyChangeItem item = new TallyChangeItem();
        item.setOrganizationId(org.getId());
        item.setChangeType(TallyChangeType.ALTER_FIELD);
        item.setField(TallyField.PAN);
        item.setLedgerKey(LEDGER_KEY);
        item.setLedgerName(LEDGER);
        item.setTallyGuid(GUID);
        item.setBaselineValue(baseline);
        item.setNewValue(newValue);
        item.setSourceType(TallyChangeSourceType.PAN_OVERRIDE);
        item.setSourceOverrideId(UUID.randomUUID());
        item.setApprovedBy("tpt_accountant");
        item.setApprovedAt(Instant.parse("2026-01-01T00:00:00Z"));
        item.setStatus(TallyChangeStatus.PENDING);
        return itemRepository.saveAndFlush(item);
    }

    private UUID upload(String token, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "masters.json", "application/json", content);
        MvcResult result = mockMvc.perform(multipart("/api/v1/uploads").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = json.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString(body.get("id").toString());
    }

    private byte[] mastersFile(String pan) throws Exception {
        ObjectNode root = json.createObjectNode();
        var entries = root.putArray("tallymessage");
        entries.addObject().putObject("group").put("name", "Duties & Taxes").put("parent", "");
        entries.addObject().putObject("ledger")
                .put("name", LEDGER)
                .put("guid", GUID)
                .put("parent", "Duties & Taxes")
                .put("istdsapplicable", "yes")
                .put("incometaxnumber", pan);
        return json.writeValueAsBytes(root);
    }

    private void awaitAppliedAndRewritten(UUID itemId, Organization org) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        TallyChangeItem item = null;
        FrozenMasterAttribute panRow = null;
        while (System.currentTimeMillis() < deadline) {
            item = itemRepository.findById(itemId).orElse(null);
            panRow = frozenPanRows(org).stream().findFirst().orElse(null);
            if (item != null && item.getStatus() == TallyChangeStatus.APPLIED
                    && panRow != null && NEW_PAN.equals(panRow.getValue())) {
                return;
            }
            Thread.sleep(100);
        }
        fail("Item " + (item == null ? "missing" : item.getStatus())
                + "; frozen PAN row " + (panRow == null ? "missing" : panRow.getValue()));
    }

    private void awaitAppliedAndCleared(UUID itemId, Organization org) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        TallyChangeItem item = null;
        FrozenMasterAttribute panRow = null;
        while (System.currentTimeMillis() < deadline) {
            item = itemRepository.findById(itemId).orElse(null);
            panRow = frozenPanRows(org).stream().findFirst().orElse(null);
            if (item != null && item.getStatus() == TallyChangeStatus.APPLIED
                    && panRow != null && panRow.getValue() == null) {
                return;
            }
            Thread.sleep(100);
        }
        fail("Item " + (item == null ? "missing" : item.getStatus())
                + "; frozen PAN row " + (panRow == null ? "missing" : panRow.getValue()));
    }

    private TallyChangeItem awaitStatus(UUID itemId, TallyChangeStatus expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        TallyChangeItem item = null;
        while (System.currentTimeMillis() < deadline) {
            item = itemRepository.findById(itemId).orElse(null);
            if (item != null && item.getStatus() == expected) {
                return item;
            }
            Thread.sleep(100);
        }
        fail("Item " + itemId + " stayed " + (item == null ? "missing" : item.getStatus())
                + ", expected " + expected);
        return null;
    }

    private TamperChange awaitTamperChange(Organization org) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            List<TamperChange> changes = tamperChangeRepository.findByOrganizationId(org.getId());
            if (!changes.isEmpty()) {
                return changes.get(0);
            }
            Thread.sleep(100);
        }
        fail("No tamper change appeared for org " + org.getId());
        return null;
    }
}
