package com.arktech.superaccountant.masters.controllers;

import com.arktech.superaccountant.login.config.DataMigrationService;
import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import com.arktech.superaccountant.login.security.jwt.JwtUtils;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for UploadController role-based access control.
 *
 * Asserts the permission matrix: ACCOUNTANT and OPERATOR may upload; only ACCOUNTANT may approve
 * or reject finding resolutions (see FindingOverrideWorkflowIT); OWNER and AUDITOR_CA are read-only; reads are open to any
 * authenticated user.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class UploadControllerIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtUtils jwtUtils;

    private String ownerJwt;
    private String accountantJwt;
    private String operatorJwt;
    private String auditorJwt;

    @BeforeEach
    void setUp() {
        Role ownerRole = roleRepository.findByName(ERole.ROLE_OWNER)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_OWNER)));
        Role accountantRole = roleRepository.findByName(ERole.ROLE_ACCOUNTANT)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_ACCOUNTANT)));
        Role operatorRole = roleRepository.findByName(ERole.ROLE_OPERATOR)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_OPERATOR)));
        Role auditorRole = roleRepository.findByName(ERole.ROLE_AUDITOR_CA)
                .orElseGet(() -> roleRepository.save(new Role(ERole.ROLE_AUDITOR_CA)));

        User ownerUser = new User("upload_owner", "upload_owner@test.com", "pass");
        ownerUser.setRole(ownerRole);
        userRepository.save(ownerUser);

        User accountantUser = new User("upload_accountant", "upload_accountant@test.com", "pass");
        accountantUser.setRole(accountantRole);
        userRepository.save(accountantUser);

        User operatorUser = new User("upload_operator", "upload_operator@test.com", "pass");
        operatorUser.setRole(operatorRole);
        userRepository.save(operatorUser);

        User auditorUser = new User("upload_auditor", "upload_auditor@test.com", "pass");
        auditorUser.setRole(auditorRole);
        userRepository.save(auditorUser);

        ownerJwt = jwtUtils.generateJwtTokenForUser("upload_owner", null);
        accountantJwt = jwtUtils.generateJwtTokenForUser("upload_accountant", null);
        operatorJwt = jwtUtils.generateJwtTokenForUser("upload_operator", null);
        auditorJwt = jwtUtils.generateJwtTokenForUser("upload_auditor", null);
    }

    // ── POST /api/v1/uploads ─────────────────────────────────────────────────

    // Scenario 1: OWNER → 403 (OWNER is read-only; cannot upload)
    @Test
    void postUploads_asOwner_returns403() throws Exception {
        MockMultipartFile fakeFile = new MockMultipartFile("file", "test.json",
                "application/json", "{}".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(fakeFile)
                        .header("Authorization", "Bearer " + ownerJwt))
                .andExpect(status().isForbidden());
    }

    // Scenario 2: ACCOUNTANT → NOT 403 (200 or 400 due to file/org validation)
    @Test
    void postUploads_asAccountant_notForbidden() throws Exception {
        MockMultipartFile fakeFile = new MockMultipartFile("file", "test.json",
                "application/json", "{}".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(fakeFile)
                        .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().is(not(403)));
    }

    // Scenario 3: OPERATOR → NOT 403
    @Test
    void postUploads_asOperator_notForbidden() throws Exception {
        MockMultipartFile fakeFile = new MockMultipartFile("file", "test.json",
                "application/json", "{}".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(fakeFile)
                        .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().is(not(403)));
    }

    // Scenario 4: AUDITOR_CA → 403 (read-only role)
    @Test
    void postUploads_asAuditorCa_returns403() throws Exception {
        MockMultipartFile fakeFile = new MockMultipartFile("file", "test.json",
                "application/json", "{}".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(fakeFile)
                        .header("Authorization", "Bearer " + auditorJwt))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/v1/uploads ──────────────────────────────────────────────────

    // Scenario 5: OWNER → NOT 403 (read is open to all authenticated users)
    @Test
    void getUploads_asOwner_notForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/uploads")
                        .header("Authorization", "Bearer " + ownerJwt))
                .andExpect(status().is(not(403)));
    }

    // Scenario 6: AUDITOR_CA → NOT 403 (read is open to all authenticated users)
    @Test
    void getUploads_asAuditorCa_notForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/uploads")
                        .header("Authorization", "Bearer " + auditorJwt))
                .andExpect(status().is(not(403)));
    }

    // ── removed PATCH /resolve and POST .../action auth ──────────────────────
    // Per-role action behaviour with real data lives in FindingOverrideWorkflowIT.

    private static final String ZERO_UUID = "00000000-0000-0000-0000-000000000000";

    // Scenario 8-10: removed self-approval endpoint
    @Test
    void patchResolve_removedEndpoint_isGoneForEveryRole() throws Exception {
        for (String jwt : new String[]{ownerJwt, accountantJwt, operatorJwt, auditorJwt}) {
            mockMvc.perform(patch("/api/v1/uploads/" + ZERO_UUID + "/mismatches/" + ZERO_UUID + "/resolve")
                            .contentType("application/json")
                            .content("{\"status\":\"APPROVED\"}")
                            .header("Authorization", "Bearer " + jwt))
                    .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                            org.hamcrest.Matchers.is(404), org.hamcrest.Matchers.is(405))));
        }
    }

    @Test
    void findingAction_withNoJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/uploads/" + ZERO_UUID + "/findings/" + ZERO_UUID + "/action")
                        .contentType("application/json")
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isUnauthorized());
    }

    // Scenario 11: No JWT → 401
    @Test
    void postUploads_withNoJwt_returns401() throws Exception {
        MockMultipartFile fakeFile = new MockMultipartFile("file", "test.json",
                "application/json", "{}".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(fakeFile))
                .andExpect(status().isUnauthorized());
    }

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UploadLedgerSnapshotRepository snapshotRepository;

    @Autowired
    private UploadJobRepository uploadJobRepository;

    @Autowired
    private DataMigrationService dataMigrationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Masters upload snapshots carry the classified ledger category.
    @Test
    void upload_snapshotsCarryLedgerCategory() throws Exception {
        Organization org = organizationRepository.save(new Organization("Category Org " + java.util.UUID.randomUUID()));
        String accountantJwt = jwtUtils.generateJwtTokenForUser("upload_accountant", org.getId());

        String masters = """
                {"tallymessage": [
                  {"group": {"name": "Indirect Expenses", "parent": ""}},
                  {"ledger": {"name": "Office Rent", "guid": "g4", "parent": "Indirect Expenses"}}
                ]}
                """;
        MockMultipartFile file = new MockMultipartFile("file", "masters.json",
                "application/json", masters.getBytes());
        String body = mockMvc.perform(multipart("/api/v1/uploads")
                        .file(file)
                        .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"").matcher(body);
        String last = null;
        while (m.find()) last = m.group(1);
        UUID jobId = java.util.UUID.fromString(last);

        java.util.List<UploadLedgerSnapshot> snapshots = snapshotRepository
                .findByUploadJobId(jobId);
        assertThat(snapshots).isNotEmpty();
        snapshots.forEach(s ->
                assertThat(s.getCategory()).isNotNull());
    }

    // Regression: an over-length PAN from Tally (11 chars vs the old varchar(10)) used to fail the
    // whole batch insert with a 500. The raw value must now be stored as-is, never truncated.
    @Test
    void upload_withOverLengthPan_keepsRawValueAndCompletes() throws Exception {
        Organization org = organizationRepository.save(
                new Organization("Overlength PAN Org " + UUID.randomUUID()));
        String jwt = jwtUtils.generateJwtTokenForUser("upload_accountant", org.getId());

        String masters = """
                {"tallymessage": [
                  {"group": {"name": "Creditors - Material Purchase", "parent": ""}},
                  {"ledger": {"name": "ANANDHA TIMBER DEPOT", "guid": "g-pan-001",
                              "parent": "Creditors - Material Purchase",
                              "incometaxnumber": "AAPPK2262D1"}}
                ]}
                """;
        MockMultipartFile file = new MockMultipartFile("file", "masters.json",
                "application/json", masters.getBytes());
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(file)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isCreated());

        UploadJob job = uploadJobRepository
                .findTopByOrganizationIdAndStatusNotInOrderByCreatedAtDesc(org.getId(),
                        java.util.List.of(UploadJobStatus.DISCARDED, UploadJobStatus.FAILED))
                .orElse(null);
        assertThat(job).isNotNull();
        java.util.List<UploadLedgerSnapshot> snapshots = snapshotRepository
                .findByUploadJobId(job.getId());
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).getPan()).isEqualTo("AAPPK2262D1");
        assertThat(snapshots.get(0).getTallyGuid()).isEqualTo("g-pan-001");
    }

    // Snapshots uploaded before the tally_guid column existed keep a null GUID; lookups by GUID
    // must not match them.
    @Test
    void snapshotWithNullTallyGuid_persistsAndIsNotFoundByGuid() {
        Organization org = organizationRepository.save(
                new Organization("Null Guid Org " + UUID.randomUUID()));
        UUID jobId = UUID.randomUUID();

        UploadLedgerSnapshot snap = new UploadLedgerSnapshot();
        snap.setUploadJobId(jobId);
        snap.setOrganizationId(org.getId());
        snap.setLedgerName("Legacy Ledger");

        UploadLedgerSnapshot saved = snapshotRepository.saveAndFlush(snap);

        assertThat(saved.getTallyGuid()).isNull();
        assertThat(snapshotRepository.findByUploadJobIdAndTallyGuid(jobId, "g-null-001"))
                .isEmpty();
    }

    // Databases created with the old varchar(10) pan must end up widened: the guarded startup
    // migration does it once, idempotently, independent of what ddl-auto does on any given boot.
    @Test
    void widenUploadLedgerSnapshotPan_widensNarrowColumnIdempotently() {
        jdbcTemplate.execute("DELETE FROM upload_ledger_snapshots");
        jdbcTemplate.execute("ALTER TABLE upload_ledger_snapshots ALTER COLUMN pan TYPE varchar(10)");
        assertThat(panColumnLength()).isEqualTo(10L);

        assertThat(dataMigrationService.widenUploadLedgerSnapshotPan()).isTrue();
        assertThat(panColumnLength()).isEqualTo(255L);

        assertThat(dataMigrationService.widenUploadLedgerSnapshotPan()).isFalse();
    }

    private long panColumnLength() {
        return jdbcTemplate.queryForObject(
                "SELECT character_maximum_length FROM information_schema.columns "
                        + "WHERE table_name = 'upload_ledger_snapshots' AND column_name = 'pan'",
                Long.class);
    }
}
