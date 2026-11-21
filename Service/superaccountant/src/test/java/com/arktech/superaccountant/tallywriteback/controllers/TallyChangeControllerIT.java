package com.arktech.superaccountant.tallywriteback.controllers;

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
import com.arktech.superaccountant.tallywriteback.models.ExistenceOutcome;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeItem;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeSourceType;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeStatus;
import com.arktech.superaccountant.tallywriteback.models.TallyChangeType;
import com.arktech.superaccountant.tallywriteback.models.TallyExportBatch;
import com.arktech.superaccountant.tallywriteback.models.TallyExportBatchItem;
import com.arktech.superaccountant.tallywriteback.models.TallyFailureReason;
import com.arktech.superaccountant.tallywriteback.models.TallyField;
import com.arktech.superaccountant.tallywriteback.repository.TallyChangeItemRepository;
import com.arktech.superaccountant.tallywriteback.repository.TallyExportBatchItemRepository;
import com.arktech.superaccountant.tallywriteback.repository.TallyExportBatchRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class TallyChangeControllerIT {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UploadJobRepository uploadJobRepository;

    @Autowired
    private UploadLedgerSnapshotRepository snapshotRepository;

    @Autowired
    private TallyChangeItemRepository itemRepository;

    @Autowired
    private TallyExportBatchRepository batchRepository;

    @Autowired
    private TallyExportBatchItemRepository batchItemRepository;

    private UUID orgId;
    private String accountantJwt;
    private String ownerJwt;
    private String operatorJwt;
    private String auditorJwt;

    @BeforeEach
    void setUp() {
        orgId = organizationRepository.save(new Organization("Tally Change Org " + UUID.randomUUID())).getId();
        accountantJwt = user("tc_accountant", ERole.ROLE_ACCOUNTANT, orgId);
        ownerJwt = user("tc_owner", ERole.ROLE_OWNER, orgId);
        operatorJwt = user("tc_operator", ERole.ROLE_OPERATOR, orgId);
        auditorJwt = user("tc_auditor", ERole.ROLE_AUDITOR_CA, orgId);
    }

    @Test
    void export_includesFoundAndRenamed_marksExportedAndRecordsBatch() throws Exception {
        UploadJob job = completedJob(orgId, Instant.now().minus(Duration.ofHours(1)));
        snapshot(job.getId(), orgId, "FOUND LEDGER", "g-found", "AAAAA1111A");
        snapshot(job.getId(), orgId, "RENAMED LEDGER NEW", "g-renamed", "BBBBB2222B");

        TallyChangeItem found = item(orgId, "found ledger", "FOUND LEDGER", "g-found",
                TallyChangeStatus.PENDING, "AAAAA1111A");
        TallyChangeItem renamed = item(orgId, "renamed ledger", "RENAMED LEDGER OLD", "g-renamed",
                TallyChangeStatus.PENDING, "BBBBB2222B");
        TallyChangeItem missing = item(orgId, "missing ledger", "MISSING LEDGER", "g-missing",
                TallyChangeStatus.PENDING, "CCCCC3333C");

        MockHttpServletResponse response = export(accountantJwt,
                exportBody(List.of(found.getId(), renamed.getId(), missing.getId()), false));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).contains("application/json").contains("UTF-8");
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"tally-pan-changes-" + today + ".json\"");
        assertThat(response.getHeader("X-Tally-Skipped")).isEqualTo("1");

        byte[] body = response.getContentAsByteArray();
        JsonNode root = objectMapper.readTree(body);
        JsonNode messages = root.get("tallymessage");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("name").asText()).isEqualTo("FOUND LEDGER");
        assertThat(messages.get(1).get("name").asText()).isEqualTo("RENAMED LEDGER NEW");
        assertThat(messages.get(1).get("metadata").get("action").asText()).isEqualTo("Alter");
        assertThat(messages.get(1).get("incometaxnumber").asText()).isEqualTo("BBBBB2222B");

        assertThat(itemRepository.findById(found.getId()).orElseThrow().getStatus())
                .isEqualTo(TallyChangeStatus.EXPORTED);
        assertThat(itemRepository.findById(renamed.getId()).orElseThrow().getStatus())
                .isEqualTo(TallyChangeStatus.EXPORTED);
        TallyChangeItem skipped = itemRepository.findById(missing.getId()).orElseThrow();
        assertThat(skipped.getStatus()).isEqualTo(TallyChangeStatus.PENDING);
        assertThat(skipped.getLastCheckOutcome()).isEqualTo(ExistenceOutcome.NOT_FOUND);
        assertThat(skipped.getLastCheckJobId()).isEqualTo(job.getId());
        assertThat(skipped.getLastCheckedAt()).isNotNull();

        List<TallyExportBatch> batches = batchRepository.findByOrganizationIdOrderByGeneratedAtDesc(orgId);
        assertThat(batches).hasSize(1);
        TallyExportBatch batch = batches.get(0);
        assertThat(batch.getGeneratedBy()).isEqualTo("tc_accountant");
        assertThat(batch.getGeneratedAt()).isNotNull();
        assertThat(batch.getMastersJobId()).isEqualTo(job.getId());
        assertThat(batch.getMastersUploadedAt()).isEqualTo(job.getCreatedAt());
        assertThat(batch.getStaleConfirmed()).isFalse();
        assertThat(batch.getItemCount()).isEqualTo(2);
        assertThat(batch.getFileSha256()).isEqualTo(sha256(body));

        List<TallyExportBatchItem> batchItems =
                batchItemRepository.findByBatchIdAndOrganizationId(batch.getId(), orgId);
        assertThat(batchItems).hasSize(2);
        assertThat(batchItems)
                .extracting(TallyExportBatchItem::getItemId, TallyExportBatchItem::getNameSent,
                        TallyExportBatchItem::getCheckOutcome)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(found.getId(), "FOUND LEDGER", ExistenceOutcome.FOUND),
                        org.assertj.core.groups.Tuple.tuple(renamed.getId(), "RENAMED LEDGER NEW", ExistenceOutcome.RENAMED));
    }

    @Test
    void export_staleUploadWithoutConfirmation_returns409AndChangesNothing() throws Exception {
        UploadJob job = completedJob(orgId, Instant.now().minus(Duration.ofHours(25)));
        snapshot(job.getId(), orgId, "STALE LEDGER", "g-stale", "AAAAA1111A");
        TallyChangeItem item = item(orgId, "stale ledger", "STALE LEDGER", "g-stale",
                TallyChangeStatus.PENDING, "AAAAA1111A");

        MockHttpServletResponse response = export(accountantJwt, exportBody(List.of(item.getId()), false));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(response.getContentAsByteArray()).get("code").asText())
                .isEqualTo("STALE_CONFIRMATION_REQUIRED");
        assertThat(batchRepository.findByOrganizationIdOrderByGeneratedAtDesc(orgId)).isEmpty();
        TallyChangeItem unchanged = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(TallyChangeStatus.PENDING);
        assertThat(unchanged.getLastCheckOutcome()).isNull();
    }

    @Test
    void export_staleUploadWithConfirmation_succeedsAndRecordsStaleConfirmed() throws Exception {
        UploadJob job = completedJob(orgId, Instant.now().minus(Duration.ofHours(25)));
        snapshot(job.getId(), orgId, "STALE LEDGER", "g-stale", "AAAAA1111A");
        TallyChangeItem item = item(orgId, "stale ledger", "STALE LEDGER", "g-stale",
                TallyChangeStatus.PENDING, "AAAAA1111A");

        MockHttpServletResponse response = export(accountantJwt, exportBody(List.of(item.getId()), true));

        assertThat(response.getStatus()).isEqualTo(200);
        List<TallyExportBatch> batches = batchRepository.findByOrganizationIdOrderByGeneratedAtDesc(orgId);
        assertThat(batches).hasSize(1);
        assertThat(batches.get(0).getStaleConfirmed()).isTrue();
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(TallyChangeStatus.EXPORTED);
    }

    @Test
    void export_noMastersUpload_returns409NoMastersUpload() throws Exception {
        TallyChangeItem item = item(orgId, "no masters", "NO MASTERS", "g-none",
                TallyChangeStatus.PENDING, "AAAAA1111A");

        MockHttpServletResponse response = export(accountantJwt, exportBody(List.of(item.getId()), true));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(response.getContentAsByteArray()).get("code").asText())
                .isEqualTo("NO_MASTERS_UPLOAD");
        assertThat(batchRepository.findByOrganizationIdOrderByGeneratedAtDesc(orgId)).isEmpty();
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(TallyChangeStatus.PENDING);
    }

    @Test
    void export_onlyBlockedItems_returns409NothingEligible() throws Exception {
        UploadJob job = completedJob(orgId, Instant.now().minus(Duration.ofHours(1)));
        snapshot(job.getId(), orgId, "PRESENT LEDGER", "g-present", "AAAAA1111A");
        TallyChangeItem missing = item(orgId, "missing", "MISSING", "g-missing",
                TallyChangeStatus.PENDING, "AAAAA1111A");
        TallyChangeItem failed = item(orgId, "failed", "FAILED LEDGER", "g-present",
                TallyChangeStatus.FAILED, "AAAAA1111A");

        MockHttpServletResponse response = export(accountantJwt,
                exportBody(List.of(missing.getId(), failed.getId()), false));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(response.getContentAsByteArray()).get("code").asText())
                .isEqualTo("NOTHING_ELIGIBLE");
        assertThat(batchRepository.findByOrganizationIdOrderByGeneratedAtDesc(orgId)).isEmpty();
        assertThat(itemRepository.findById(missing.getId()).orElseThrow().getStatus())
                .isEqualTo(TallyChangeStatus.PENDING);
        assertThat(itemRepository.findById(failed.getId()).orElseThrow().getStatus())
                .isEqualTo(TallyChangeStatus.FAILED);
    }

    @Test
    void export_asOperatorOrAuditor_returns403() throws Exception {
        UploadJob job = completedJob(orgId, Instant.now().minus(Duration.ofHours(1)));
        snapshot(job.getId(), orgId, "ROLE LEDGER", "g-role", "AAAAA1111A");
        TallyChangeItem item = item(orgId, "role ledger", "ROLE LEDGER", "g-role",
                TallyChangeStatus.PENDING, "AAAAA1111A");

        assertThat(export(operatorJwt, exportBody(List.of(item.getId()), false)).getStatus()).isEqualTo(403);
        assertThat(export(auditorJwt, exportBody(List.of(item.getId()), false)).getStatus()).isEqualTo(403);
        assertThat(batchRepository.findByOrganizationIdOrderByGeneratedAtDesc(orgId)).isEmpty();
    }

    @Test
    void export_ignoresOtherOrganizationsItems() throws Exception {
        Organization otherOrg = organizationRepository.save(new Organization("Other Org " + UUID.randomUUID()));
        UploadJob job = completedJob(orgId, Instant.now().minus(Duration.ofHours(1)));
        snapshot(job.getId(), orgId, "MINE", "g-mine", "AAAAA1111A");
        UploadJob otherJob = completedJob(otherOrg.getId(), Instant.now().minus(Duration.ofHours(1)));
        snapshot(otherJob.getId(), otherOrg.getId(), "THEIRS", "g-theirs", "BBBBB2222B");

        TallyChangeItem mine = item(orgId, "mine", "MINE", "g-mine",
                TallyChangeStatus.PENDING, "AAAAA1111A");
        TallyChangeItem theirs = item(otherOrg.getId(), "theirs", "THEIRS", "g-theirs",
                TallyChangeStatus.PENDING, "BBBBB2222B");

        MockHttpServletResponse response = export(accountantJwt,
                exportBody(List.of(mine.getId(), theirs.getId()), false));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-Tally-Skipped")).isEqualTo("0");
        assertThat(objectMapper.readTree(response.getContentAsByteArray()).get("tallymessage"))
                .hasSize(1);
        assertThat(itemRepository.findById(theirs.getId()).orElseThrow().getStatus())
                .isEqualTo(TallyChangeStatus.PENDING);
        List<TallyExportBatch> batches = batchRepository.findByOrganizationIdOrderByGeneratedAtDesc(orgId);
        assertThat(batches).hasSize(1);
        assertThat(batchItemRepository.findByBatchIdAndOrganizationId(batches.get(0).getId(), orgId))
                .extracting(TallyExportBatchItem::getItemId)
                .containsExactly(mine.getId());
    }

    @Test
    void export_allItemsWhenIdsEmpty_andAllowsReExportOfExportedItems() throws Exception {
        UploadJob job = completedJob(orgId, Instant.now().minus(Duration.ofHours(1)));
        snapshot(job.getId(), orgId, "REEXPORT LEDGER", "g-reexport", "AAAAA1111A");
        TallyChangeItem item = item(orgId, "reexport ledger", "REEXPORT LEDGER", "g-reexport",
                TallyChangeStatus.PENDING, "AAAAA1111A");

        MockHttpServletResponse first = export(accountantJwt, exportBody(null, false));
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(TallyChangeStatus.EXPORTED);

        MockHttpServletResponse second = export(accountantJwt, exportBody(List.of(), false));
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(second.getContentAsByteArray()).get("tallymessage"))
                .hasSize(1);
        assertThat(batchRepository.findByOrganizationIdOrderByGeneratedAtDesc(orgId)).hasSize(2);
        assertThat(itemRepository.findById(item.getId()).orElseThrow().getStatus())
                .isEqualTo(TallyChangeStatus.EXPORTED);
    }

    @Test
    void export_asOwner_isAllowed() throws Exception {
        UploadJob job = completedJob(orgId, Instant.now().minus(Duration.ofHours(1)));
        snapshot(job.getId(), orgId, "OWNER LEDGER", "g-owner", "AAAAA1111A");
        TallyChangeItem item = item(orgId, "owner ledger", "OWNER LEDGER", "g-owner",
                TallyChangeStatus.PENDING, "AAAAA1111A");

        assertThat(export(ownerJwt, exportBody(List.of(item.getId()), false)).getStatus()).isEqualTo(200);
    }

    @Test
    void retry_failedValueChanged_resetsToPendingWithObservedBaseline() throws Exception {
        TallyChangeItem failed = item(orgId, "retry changed", "RETRY CHANGED", "g-retry-changed",
                TallyChangeStatus.FAILED, "BBBBB2222B");
        failed.setObservedValue("BBBBB2222B");
        failed.setFailureReason(TallyFailureReason.TALLY_VALUE_CHANGED);
        failed.setResolvedAt(Instant.parse("2026-03-01T00:00:00Z"));
        itemRepository.saveAndFlush(failed);

        MockHttpServletResponse response = retry(accountantJwt, failed.getId());

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("id").asText()).isEqualTo(failed.getId().toString());
        assertThat(body.get("status").asText()).isEqualTo("PENDING");
        assertThat(body.get("baselineValue").asText()).isEqualTo("BBBBB2222B");
        assertThat(body.get("failureReason").isNull()).isTrue();
        assertThat(body.get("resolvedAt").isNull()).isTrue();

        TallyChangeItem loaded = itemRepository.findById(failed.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(TallyChangeStatus.PENDING);
        assertThat(loaded.getBaselineValue()).isEqualTo("BBBBB2222B");
        assertThat(loaded.getFailureReason()).isNull();
        assertThat(loaded.getResolvedAt()).isNull();
        assertThat(batchRepository.findByOrganizationIdOrderByGeneratedAtDesc(orgId)).isEmpty();
    }

    @Test
    void retry_failedLedgerMissingWithNullObserved_leavesBaselineNull() throws Exception {
        TallyChangeItem failed = item(orgId, "retry missing", "RETRY MISSING", "g-retry-missing",
                TallyChangeStatus.FAILED, "BBBBB2222B");
        failed.setObservedValue(null);
        failed.setFailureReason(TallyFailureReason.LEDGER_MISSING);
        itemRepository.saveAndFlush(failed);

        MockHttpServletResponse response = retry(accountantJwt, failed.getId());

        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("status").asText()).isEqualTo("PENDING");
        assertThat(body.get("baselineValue").isNull()).isTrue();
        assertThat(body.get("failureReason").isNull()).isTrue();

        TallyChangeItem loaded = itemRepository.findById(failed.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(TallyChangeStatus.PENDING);
        assertThat(loaded.getBaselineValue()).isNull();
        assertThat(loaded.getFailureReason()).isNull();
    }

    @Test
    void retry_nonFailedStatuses_return409AndLeaveStateUnchanged() throws Exception {
        for (TallyChangeStatus status : List.of(TallyChangeStatus.PENDING, TallyChangeStatus.EXPORTED,
                TallyChangeStatus.APPLIED, TallyChangeStatus.SUPERSEDED)) {
            TallyChangeItem existing = item(orgId, "retry " + status.name().toLowerCase(),
                    "RETRY " + status.name(), "g-retry-" + status.name().toLowerCase(),
                    status, "BBBBB2222B");
            existing.setObservedValue("BBBBB2222B");
            existing.setFailureReason(TallyFailureReason.TALLY_VALUE_CHANGED);
            itemRepository.saveAndFlush(existing);

            MockHttpServletResponse response = retry(accountantJwt, existing.getId());

            assertThat(response.getStatus()).isEqualTo(409);
            TallyChangeItem loaded = itemRepository.findById(existing.getId()).orElseThrow();
            assertThat(loaded.getStatus()).isEqualTo(status);
            assertThat(loaded.getBaselineValue()).isEqualTo("OLDVALUE00X");
            assertThat(loaded.getFailureReason()).isEqualTo(TallyFailureReason.TALLY_VALUE_CHANGED);
        }
    }

    @Test
    void retry_unknownOrForeignItem_returns404() throws Exception {
        Organization otherOrg = organizationRepository.save(new Organization("Other Org " + UUID.randomUUID()));
        TallyChangeItem theirs = item(otherOrg.getId(), "theirs retry", "THEIRS RETRY", "g-theirs-retry",
                TallyChangeStatus.FAILED, "BBBBB2222B");
        theirs.setObservedValue("BBBBB2222B");
        theirs.setFailureReason(TallyFailureReason.TALLY_VALUE_CHANGED);
        itemRepository.saveAndFlush(theirs);

        assertThat(retry(accountantJwt, UUID.randomUUID()).getStatus()).isEqualTo(404);
        assertThat(retry(accountantJwt, theirs.getId()).getStatus()).isEqualTo(404);

        TallyChangeItem loaded = itemRepository.findById(theirs.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(TallyChangeStatus.FAILED);
        assertThat(loaded.getFailureReason()).isEqualTo(TallyFailureReason.TALLY_VALUE_CHANGED);
    }

    @Test
    void retry_asOperatorOrAuditor_returns403_ownerAllowed() throws Exception {
        TallyChangeItem failed = item(orgId, "role retry", "ROLE RETRY", "g-role-retry",
                TallyChangeStatus.FAILED, "BBBBB2222B");
        failed.setObservedValue("BBBBB2222B");
        failed.setFailureReason(TallyFailureReason.TALLY_VALUE_CHANGED);
        itemRepository.saveAndFlush(failed);

        assertThat(retry(operatorJwt, failed.getId()).getStatus()).isEqualTo(403);
        assertThat(retry(auditorJwt, failed.getId()).getStatus()).isEqualTo(403);
        assertThat(itemRepository.findById(failed.getId()).orElseThrow().getStatus())
                .isEqualTo(TallyChangeStatus.FAILED);

        assertThat(retry(ownerJwt, failed.getId()).getStatus()).isEqualTo(200);
        assertThat(itemRepository.findById(failed.getId()).orElseThrow().getStatus())
                .isEqualTo(TallyChangeStatus.PENDING);
    }

    @Test
    void get_returnsMastersUploadAndItemsNewestFirstWithLivePreview() throws Exception {
        UploadJob job = completedJob(orgId, Instant.now().minus(Duration.ofHours(1)));
        snapshot(job.getId(), orgId, "PREVIEW NEW NAME", "g-preview", "AAAAA1111A");
        snapshot(job.getId(), orgId, "FOUND LEDGER", "g-found", "BBBBB2222B");

        TallyChangeItem older = item(orgId, "found ledger", "FOUND LEDGER", "g-found",
                TallyChangeStatus.PENDING, "BBBBB2222B", Instant.parse("2026-01-01T00:00:00Z"));
        TallyChangeItem newer = item(orgId, "preview ledger", "PREVIEW OLD NAME", "g-preview",
                TallyChangeStatus.PENDING, "AAAAA1111A", Instant.parse("2026-02-01T00:00:00Z"));
        TallyChangeItem history = item(orgId, "history ledger", "HISTORY LEDGER", "g-history",
                TallyChangeStatus.APPLIED, "CCCCC3333C", Instant.parse("2025-12-01T00:00:00Z"));

        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/tally-changes")
                        .header("Authorization", "Bearer " + auditorJwt))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        JsonNode root = objectMapper.readTree(response.getContentAsByteArray());
        JsonNode upload = root.get("mastersUpload");
        assertThat(upload.get("jobId").asText()).isEqualTo(job.getId().toString());
        assertThat(upload.get("stale").asBoolean()).isFalse();
        assertThat(upload.get("uploadedAt").asText()).isNotBlank();

        JsonNode items = root.get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("id").asText()).isEqualTo(newer.getId().toString());
        assertThat(items.get(1).get("id").asText()).isEqualTo(older.getId().toString());
        assertThat(items.get(2).get("id").asText()).isEqualTo(history.getId().toString());

        JsonNode preview = items.get(0);
        assertThat(preview.get("checkOutcome").asText()).isEqualTo("RENAMED");
        assertThat(preview.get("checkReason").isNull()).isTrue();
        assertThat(preview.get("currentTallyName").asText()).isEqualTo("PREVIEW NEW NAME");
        assertThat(preview.get("currentTallyPan").asText()).isEqualTo("AAAAA1111A");
        assertThat(preview.get("ledgerKey").asText()).isEqualTo("preview ledger");
        assertThat(preview.get("ledgerName").asText()).isEqualTo("PREVIEW OLD NAME");
        assertThat(preview.get("changeType").asText()).isEqualTo("ALTER_FIELD");
        assertThat(preview.get("field").asText()).isEqualTo("PAN");
        assertThat(preview.get("status").asText()).isEqualTo("PENDING");
        assertThat(preview.get("sourceType").asText()).isEqualTo("PAN_OVERRIDE");

        JsonNode historyPreview = items.get(2);
        assertThat(historyPreview.get("status").asText()).isEqualTo("APPLIED");
        assertThat(historyPreview.get("checkOutcome").asText()).isEqualTo("NOT_FOUND");
        assertThat(historyPreview.get("checkReason").isNull()).isTrue();

        List<String> fields = new ArrayList<>();
        preview.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(
                "id", "ledgerKey", "ledgerName", "changeType", "field", "baselineValue",
                "newValue", "status", "sourceType", "approvedBy", "approvedAt", "observedValue",
                "failureReason", "lastCheckOutcome", "lastCheckedAt", "createdAt", "resolvedAt",
                "checkOutcome", "checkReason", "currentTallyName", "currentTallyPan");
    }

    @Test
    void get_withoutMastersUpload_returnsNullUploadAndNoMastersReason() throws Exception {
        TallyChangeItem item = item(orgId, "no masters", "NO MASTERS", "g-none",
                TallyChangeStatus.PENDING, "AAAAA1111A");

        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/tally-changes")
                        .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        JsonNode root = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(root.get("mastersUpload").isNull()).isTrue();
        JsonNode preview = root.get("items").get(0);
        assertThat(preview.get("id").asText()).isEqualTo(item.getId().toString());
        assertThat(preview.get("checkOutcome").asText()).isEqualTo("NOT_FOUND");
        assertThat(preview.get("checkReason").asText()).isEqualTo("NO_MASTERS_UPLOAD");
    }

    @Test
    void get_filtersByStatus() throws Exception {
        UploadJob job = completedJob(orgId, Instant.now().minus(Duration.ofHours(1)));
        snapshot(job.getId(), orgId, "FILTER LEDGER", "g-filter", "AAAAA1111A");
        TallyChangeItem pending = item(orgId, "pending", "FILTER LEDGER", "g-filter",
                TallyChangeStatus.PENDING, "AAAAA1111A");
        TallyChangeItem failed = item(orgId, "failed", "FILTER LEDGER", "g-filter",
                TallyChangeStatus.FAILED, "AAAAA1111A");

        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/tally-changes")
                        .param("status", "FAILED")
                        .header("Authorization", "Bearer " + accountantJwt))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        JsonNode items = objectMapper.readTree(response.getContentAsByteArray()).get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("id").asText()).isEqualTo(failed.getId().toString());
        assertThat(items.get(0).get("status").asText()).isEqualTo("FAILED");
        assertThat(items.get(0).get("id").asText()).isNotEqualTo(pending.getId().toString());
    }

    @Test
    void get_withNoJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tally-changes"))
                .andExpect(status().isUnauthorized());
    }

    private String user(String username, ERole roleName, UUID organizationId) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User(username, username + "@test.com", "pass");
            user.setRole(role);
            userRepository.save(user);
        }
        return jwtUtils.generateJwtTokenForUser(username, organizationId);
    }

    private UploadJob completedJob(UUID organizationId, Instant createdAt) {
        UploadJob job = new UploadJob();
        job.setOrganizationId(organizationId);
        job.setFileName("masters.json");
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setUploadedBy("tc_accountant");
        job.setCreatedAt(createdAt);
        return uploadJobRepository.saveAndFlush(job);
    }

    private UploadLedgerSnapshot snapshot(UUID jobId, UUID organizationId, String ledgerName,
                                          String tallyGuid, String pan) {
        UploadLedgerSnapshot snapshot = new UploadLedgerSnapshot();
        snapshot.setUploadJobId(jobId);
        snapshot.setOrganizationId(organizationId);
        snapshot.setLedgerName(ledgerName);
        snapshot.setTallyGuid(tallyGuid);
        snapshot.setPan(pan);
        return snapshotRepository.saveAndFlush(snapshot);
    }

    private TallyChangeItem item(UUID organizationId, String ledgerKey, String ledgerName,
                                 String tallyGuid, TallyChangeStatus status, String newValue) {
        return item(organizationId, ledgerKey, ledgerName, tallyGuid, status, newValue, null);
    }

    private TallyChangeItem item(UUID organizationId, String ledgerKey, String ledgerName,
                                 String tallyGuid, TallyChangeStatus status, String newValue,
                                 Instant createdAt) {
        TallyChangeItem item = new TallyChangeItem();
        item.setOrganizationId(organizationId);
        item.setChangeType(TallyChangeType.ALTER_FIELD);
        item.setField(TallyField.PAN);
        item.setLedgerKey(ledgerKey);
        item.setLedgerName(ledgerName);
        item.setTallyGuid(tallyGuid);
        item.setBaselineValue("OLDVALUE00X");
        item.setNewValue(newValue);
        item.setSourceType(TallyChangeSourceType.PAN_OVERRIDE);
        item.setSourceOverrideId(UUID.randomUUID());
        item.setApprovedBy("tc_accountant");
        item.setApprovedAt(Instant.parse("2026-01-01T00:00:00Z"));
        item.setStatus(status);
        if (createdAt != null) {
            item.setCreatedAt(createdAt);
        }
        return itemRepository.saveAndFlush(item);
    }

    private String exportBody(List<UUID> itemIds, Boolean confirmStale) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("itemIds", itemIds);
        body.put("confirmStale", confirmStale);
        return objectMapper.writeValueAsString(body);
    }

    private MockHttpServletResponse export(String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/tally-changes/export")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse retry(String token, UUID id) throws Exception {
        return mockMvc.perform(post("/api/v1/tally-changes/" + id + "/retry")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
