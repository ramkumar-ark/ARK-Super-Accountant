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
import com.arktech.superaccountant.tallywriteback.models.TallyFailureReason;
import com.arktech.superaccountant.tallywriteback.models.TallyField;
import com.arktech.superaccountant.tallywriteback.repository.TallyChangeItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end over HTTP, not @Transactional: the reconciliation listener fires on
 * the masters intake commit, so state must really be committed between steps.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TallyChangeReconcileUploadIT {

    private static final String GUID = "guid-test-001";
    private static final String LEDGER = "Test Cement Purchases";
    private static final String BASELINE = "AAAAA1111A";
    private static final String NEW_PAN = "BBBBB2222B";

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

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void uploadWithNewPan_marksItemApplied() throws Exception {
        Organization org = organization();
        String token = token(org);
        TallyChangeItem item = pendingItem(org, BASELINE, NEW_PAN);

        UUID jobId = upload(token, fixtureBytes(NEW_PAN));

        TallyChangeItem reconciled = awaitStatus(item.getId(), TallyChangeStatus.APPLIED);
        assertThat(reconciled.getAppliedUploadJobId()).isEqualTo(jobId);
        assertThat(reconciled.getResolvedAt()).isNotNull();
        assertThat(reconciled.getFailureReason()).isNull();
    }

    @Test
    void uploadWithDifferentPan_marksItemFailedWithObservedValue() throws Exception {
        Organization org = organization();
        String token = token(org);
        TallyChangeItem item = pendingItem(org, BASELINE, NEW_PAN);

        upload(token, fixtureBytes("CCCCC3333C"));

        TallyChangeItem reconciled = awaitStatus(item.getId(), TallyChangeStatus.FAILED);
        assertThat(reconciled.getFailureReason())
                .isEqualTo(TallyFailureReason.TALLY_VALUE_CHANGED);
        assertThat(reconciled.getObservedValue())
                .isEqualTo("CCCCC3333C");
        assertThat(reconciled.getAppliedUploadJobId()).isNull();
    }

    @Test
    void uploadForOneOrg_doesNotTouchAnotherOrgsItem() throws Exception {
        Organization mine = organization();
        Organization other = organization();
        String token = token(mine);
        TallyChangeItem mineItem = pendingItem(mine, BASELINE, NEW_PAN);
        TallyChangeItem otherItem = pendingItem(other, BASELINE, NEW_PAN);

        upload(token, fixtureBytes(NEW_PAN));

        awaitStatus(mineItem.getId(), TallyChangeStatus.APPLIED);
        TallyChangeItem untouched = itemRepository.findById(otherItem.getId()).orElseThrow();
        assertThat(untouched.getStatus())
                .isEqualTo(TallyChangeStatus.PENDING);
        assertThat(untouched.getAppliedUploadJobId()).isNull();
        assertThat(untouched.getFailureReason()).isNull();
    }

    private Organization organization() {
        return organizationRepository.save(new Organization("Tally Reconcile Org " + UUID.randomUUID()));
    }

    private String token(Organization org) {
        String username = "tcr_" + UUID.randomUUID().toString().substring(0, 8);
        Role role = roleRepository.findByName(ERole.ROLE_ACCOUNTANT)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_ACCOUNTANT)));
        User user = new User(username, username + "@test.com", "pass");
        user.setRole(role);
        userRepository.save(user);
        return jwtUtils.generateJwtTokenForUser(username, org.getId());
    }

    private TallyChangeItem pendingItem(Organization org, String baseline, String newValue) {
        TallyChangeItem item = new TallyChangeItem();
        item.setOrganizationId(org.getId());
        item.setChangeType(TallyChangeType.ALTER_FIELD);
        item.setField(TallyField.PAN);
        item.setLedgerKey(LEDGER.trim().toLowerCase());
        item.setLedgerName(LEDGER);
        item.setTallyGuid(GUID);
        item.setBaselineValue(baseline);
        item.setNewValue(newValue);
        item.setSourceType(TallyChangeSourceType.PAN_OVERRIDE);
        item.setSourceOverrideId(UUID.randomUUID());
        item.setApprovedBy("tcr_accountant");
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

    private TallyChangeItem awaitStatus(UUID itemId, TallyChangeStatus expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        TallyChangeItem item = itemRepository.findById(itemId).orElseThrow();
        while (System.currentTimeMillis() < deadline) {
            item = itemRepository.findById(itemId).orElseThrow();
            if (item.getStatus() == expected) {
                return item;
            }
            Thread.sleep(100);
        }
        fail("Item " + itemId + " stayed " + item.getStatus() + ", expected " + expected);
        return item;
    }

    private byte[] fixtureBytes(String pan) throws Exception {
        JsonNode root = json.readTree(
                getClass().getResourceAsStream("/fixtures/masters-minimal.json").readAllBytes());
        for (JsonNode entry : root.get("tallymessage")) {
            JsonNode ledger = entry.get("ledger");
            if (ledger != null && GUID.equals(ledger.path("guid").asText())) {
                ((ObjectNode) ledger).put("incometaxnumber", pan);
            }
        }
        return json.writeValueAsBytes(root);
    }
}
