package com.arktech.superaccountant.gstr1.services;

import com.arktech.superaccountant.daybook.models.DayBookUploadJob;
import com.arktech.superaccountant.daybook.models.ParsedVoucher;
import com.arktech.superaccountant.daybook.models.ParsedVoucherEntry;
import com.arktech.superaccountant.daybook.repository.DayBookUploadJobRepository;
import com.arktech.superaccountant.daybook.repository.ParsedVoucherRepository;
import com.arktech.superaccountant.gstr1.models.Gstr1FillInAttribute;
import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository;
import com.arktech.superaccountant.gst.mapping.GstHead;
import com.arktech.superaccountant.gst.mapping.TaxLedgerMappingRepository;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadLedgerSnapshotRepository;
import com.arktech.superaccountant.workflow.models.Workflow;
import com.arktech.superaccountant.workflow.models.WorkflowStage;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import com.arktech.superaccountant.workflow.repository.WorkflowRepository;
import com.arktech.superaccountant.workflow.snapshot.AmendmentChangeKind;
import com.arktech.superaccountant.workflow.snapshot.AmendmentFollowUp;
import com.arktech.superaccountant.workflow.snapshot.AmendmentStatus;
import com.arktech.superaccountant.workflow.snapshot.VoucherKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Amendment and late-reporting scenarios S1-S3 over a real database (plan T20).
 * Not @Transactional: prepare/build commits must be visible to the finalize gate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "JWT_SECRET=test-jwt-secret-must-be-at-least-32-characters-long")
class Gstr1AmendmentFlowIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Gstr1PrepareService prepareService;
    @Autowired
    private com.arktech.superaccountant.gstr1.validation.Gstr1FinalizePrecondition finalizePrecondition;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private DayBookUploadJobRepository dayBookUploadJobRepository;
    @Autowired
    private ParsedVoucherRepository parsedVoucherRepository;
    @Autowired
    private WorkflowRepository workflowRepository;
    @Autowired
    private UploadLedgerSnapshotRepository snapshotRepository;
    @Autowired
    private TaxLedgerMappingRepository taxLedgerMappingRepository;
    @Autowired
    private PreparedReturnRepository preparedReturnRepository;
    @Autowired
    private PreparedReturnRowRepository rowRepository;
    @Autowired
    private com.arktech.superaccountant.workflow.snapshot.AmendmentFollowUpRepository followUpRepository;
    @Autowired
    private com.arktech.superaccountant.workflow.service.WorkflowService workflowService;

    private final ObjectMapper json = new ObjectMapper();

    private UUID orgId;
    private String ownerToken;
    private String accountantToken;
    private int counter = 0;

    // ---------- S1: late-reported voucher ----------

    @Test
    void lateVoucherFromAprilLandsInMayReturnFlaggedLate() throws Exception {
        setup();
        Workflow april = frozenAprilReturn();
        DayBookUploadJob job2 = mayJob();

        // a new voucher dated 2025-04-30 is recorded in May's books
        seedSalesInvoice(orgId, job2.getId(), "may-9", "INV-9", LocalDate.of(2025, 4, 30));
        Workflow may = mayWorkflow(job2);
        String lateKey = "G:may-9";

        AmendmentFollowUp followUp = new AmendmentFollowUp();
        followUp.setOrganizationId(orgId);
        followUp.setWorkflowId(april.getId());
        followUp.setVoucherKey(lateKey);
        followUp.setStatus(AmendmentStatus.OPEN);
        followUp.setChange(AmendmentChangeKind.LATE);
        followUp.setCreatedAt(Instant.now());
        followUp = followUpRepository.save(followUp);

        PreparedReturn mayReturn = prepareService.buildOrRebuild(may);

        List<PreparedReturnRow> rows = rowRepository.findByPreparedReturnId(mayReturn.getId());
        List<PreparedReturnRow> lateRows = rows.stream()
                .filter(r -> lateKey.equals(r.getSourceVoucherKey()))
                .toList();
        assertThat(lateRows).hasSize(1);
        PreparedReturnRow row = lateRows.get(0);
        assertThat(row.getTableCode()).isEqualTo("B2B");
        assertThat(row.isAmendment()).isFalse();
        assertThat(row.isLateReporting()).isTrue();
        assertThat(row.getFollowUpId()).isEqualTo(followUp.getId());
        assertThat(row.payload().get("invoiceNo")).isEqualTo("INV-9");
        // the late row is the only B2B row: the liability is not doubled
        assertThat(rows.stream().filter(r -> "B2B".equals(r.getTableCode())).count()).isEqualTo(1);
    }

    // ---------- S2: changed invoice, original number/date from the frozen return ----------

    @Test
    void changedInvoiceGetsOriginalNumberAndDateFromAprilFrozenReturn() throws Exception {
        setup();
        Workflow april = frozenAprilReturn();
        DayBookUploadJob job2 = mayJob();

        // the April invoice is corrected in May's books (same guid, new number)
        seedSalesInvoice(orgId, job2.getId(), "apr-1", "INV-001-REV", LocalDate.of(2025, 4, 15));
        Workflow may = mayWorkflow(job2);
        String key = "G:apr-1";

        PreparedReturnRow originalRow = rowOf(april.getId(), "b2b:" + key).orElseThrow();
        assertThat(originalRow.payload().get("invoiceNo")).isEqualTo("INV-001");

        AmendmentFollowUp followUp = new AmendmentFollowUp();
        followUp.setOrganizationId(orgId);
        followUp.setWorkflowId(april.getId());
        followUp.setVoucherKey(key);
        followUp.setStatus(AmendmentStatus.OPEN);
        followUp.setChange(AmendmentChangeKind.CHANGED);
        followUp.setTableCode("B2B");
        followUp.setOriginalWorkflowId(april.getId());
        followUp.setOriginalDocKey("b2b:" + key);
        followUp.setCreatedAt(Instant.now());
        followUpRepository.save(followUp);

        PreparedReturn mayReturn = prepareService.buildOrRebuild(may);

        List<PreparedReturnRow> amendRows = rowRepository.findByPreparedReturnId(mayReturn.getId())
                .stream().filter(PreparedReturnRow::isAmendment).toList();
        assertThat(amendRows).hasSize(1);
        PreparedReturnRow row = amendRows.get(0);
        assertThat(row.getTableCode()).isEqualTo("B2BA");
        assertThat(row.getOriginalPeriod()).isEqualTo("2025-04");
        assertThat(row.getOriginalDocKey()).isEqualTo("b2b:" + key);
        Map<String, Object> payload = row.payload();
        assertThat(payload.get("oinum")).isEqualTo("INV-001");
        assertThat(payload.get("oidt")).isEqualTo("2025-04-15");
        assertThat(payload.get("invoiceNo")).isEqualTo("INV-001-REV");
        // no duplicate normal-table row for the revised April-dated voucher
        assertThat(rowRepository.findByPreparedReturnId(mayReturn.getId()).stream()
                .filter(r -> key.equals(r.getSourceVoucherKey()) && !r.isAmendment()).count())
                .isZero();
    }

    // ---------- S3: deletion → amendment to zero behind the accountant ack ----------

    @Test
    void deletedInvoiceNeedsAcknowledgementThenProducesZeroedAmendmentRow() throws Exception {
        setup();
        Workflow april = frozenAprilReturn();
        Workflow may = mayWorkflow(mayJob());

        String key = "G:apr-1";
        AmendmentFollowUp followUp = new AmendmentFollowUp();
        followUp.setOrganizationId(orgId);
        followUp.setWorkflowId(april.getId());
        followUp.setVoucherKey(key);
        followUp.setStatus(AmendmentStatus.OPEN);
        followUp.setChange(AmendmentChangeKind.DELETED);
        followUp.setTableCode("B2B");
        followUp.setOriginalWorkflowId(april.getId());
        followUp.setOriginalDocKey("b2b:" + key);
        followUp.setCreatedAt(Instant.now());
        followUpRepository.save(followUp);

        PreparedReturn mayReturn = prepareService.buildOrRebuild(may);

        // without the ack: no amendment row, finalize gate blocks
        assertThat(rowRepository.findByPreparedReturnId(mayReturn.getId()).stream()
                .anyMatch(PreparedReturnRow::isAmendment)).isFalse();
        List<String> unmet = finalizePrecondition.unmet(may);
        assertThat(unmet).anySatisfy(message -> {
            assertThat(message).contains("AMENDMENT_ACK_MISSING");
            assertThat(message).contains(key);
        });

        // the accountant acknowledges via the fill-ins endpoint
        mockMvc.perform(put("/api/v1/workflows/" + may.getId() + "/gstr1/fill-ins")
                        .header("Authorization", "Bearer " + accountantToken)
                        .contentType("application/json")
                        .content("{\"scope\":\"VOUCHER\",\"key\":\"" + key + "\","
                                + "\"attribute\":\"AMENDMENT_ZERO_ACK\",\"value\":\"ACK\"}"))
                .andExpect(status().isOk());

        mayReturn = prepareService.buildOrRebuild(may);
        List<PreparedReturnRow> amendRows = rowRepository.findByPreparedReturnId(mayReturn.getId())
                .stream().filter(PreparedReturnRow::isAmendment).toList();
        assertThat(amendRows).hasSize(1);
        PreparedReturnRow row = amendRows.get(0);
        assertThat(row.getTableCode()).isEqualTo("B2BA");
        Map<String, Object> payload = row.payload();
        assertThat(payload.get("oinum")).isEqualTo("INV-001");
        assertThat(new BigDecimal(payload.get("taxableValue").toString()))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(new BigDecimal(payload.get("invoiceValue").toString()))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(finalizePrecondition.unmet(may)).isEmpty();
    }

    // ---------- S6: follow-up closure at finalize ----------

    /** Clearing the GSTIN keeps the tax-mapping finding source from blocking finalize. */
    private void clearGstin() {
        Organization org = organizationRepository.findById(orgId).orElseThrow();
        org.setGstin(null);
        organizationRepository.save(org);
    }

    @Test
    void finalizeClosesFollowUpsWhoseRowsWereIncluded() throws Exception {
        setup();
        clearGstin();
        Workflow april = frozenAprilReturn();
        DayBookUploadJob job2 = mayJob();

        // one CHANGED follow-up (produces a B2BA amendment row) and one LATE follow-up
        seedSalesInvoice(orgId, job2.getId(), "apr-1", "INV-001-REV", LocalDate.of(2025, 4, 15));
        seedSalesInvoice(orgId, job2.getId(), "may-9", "INV-9", LocalDate.of(2025, 4, 30));
        Workflow may = mayWorkflow(job2);

        AmendmentFollowUp changed = new AmendmentFollowUp();
        changed.setOrganizationId(orgId);
        changed.setWorkflowId(april.getId());
        changed.setVoucherKey("G:apr-1");
        changed.setStatus(AmendmentStatus.OPEN);
        changed.setChange(AmendmentChangeKind.CHANGED);
        changed.setTableCode("B2B");
        changed.setOriginalWorkflowId(april.getId());
        changed.setOriginalDocKey("b2b:G:apr-1");
        changed.setCreatedAt(Instant.now());
        changed = followUpRepository.save(changed);

        AmendmentFollowUp late = new AmendmentFollowUp();
        late.setOrganizationId(orgId);
        late.setWorkflowId(april.getId());
        late.setVoucherKey("G:may-9");
        late.setStatus(AmendmentStatus.OPEN);
        late.setChange(AmendmentChangeKind.LATE);
        late.setCreatedAt(Instant.now());
        late = followUpRepository.save(late);

        prepareService.buildOrRebuild(may);
        workflowService.finalize(orgId, "acc", may.getId());

        changed = followUpRepository.findById(changed.getId()).orElseThrow();
        assertThat(changed.getStatus()).isEqualTo(AmendmentStatus.DONE);
        assertThat(changed.getDoneBy()).isEqualTo("acc");
        assertThat(changed.getDoneAt()).isNotNull();
        assertThat(changed.getDoneNote()).contains("2025-05");
        late = followUpRepository.findById(late.getId()).orElseThrow();
        assertThat(late.getStatus()).isEqualTo(AmendmentStatus.DONE);
        assertThat(late.getDoneBy()).isEqualTo("acc");
    }

    @Test
    void finalizeDoesNotCloseFollowUpsWithoutRows_andClosureSurvivesRebuild() throws Exception {
        setup();
        clearGstin();
        Workflow april = frozenAprilReturn();
        Workflow may = mayWorkflow(mayJob());

        // CHANGED follow-up without a matching voucher in May's books: no row
        AmendmentFollowUp unmatched = new AmendmentFollowUp();
        unmatched.setOrganizationId(orgId);
        unmatched.setWorkflowId(april.getId());
        unmatched.setVoucherKey("G:apr-1");
        unmatched.setStatus(AmendmentStatus.OPEN);
        unmatched.setChange(AmendmentChangeKind.CHANGED);
        unmatched.setTableCode("B2B");
        unmatched.setOriginalWorkflowId(april.getId());
        unmatched.setOriginalDocKey("b2b:G:apr-1");
        unmatched.setCreatedAt(Instant.now());
        unmatched = followUpRepository.save(unmatched);

        prepareService.buildOrRebuild(may);
        workflowService.finalize(orgId, "acc", may.getId());
        unmatched = followUpRepository.findById(unmatched.getId()).orElseThrow();
        assertThat(unmatched.getStatus()).isEqualTo(AmendmentStatus.OPEN);

        // unfinalize + rebuild: closed follow-ups stay DONE, no re-closure, no error
        workflowService.unfinalize(orgId, "acc", may.getId(), "correction needed");
        prepareService.buildOrRebuild(workflowRepository.findById(may.getId()).orElseThrow());
        assertThat(followUpRepository.findById(unmatched.getId()).orElseThrow().getStatus())
                .isEqualTo(AmendmentStatus.OPEN);

        // and a follow-up already closed at finalize stays DONE after the rebuild
        AmendmentFollowUp closed = new AmendmentFollowUp();
        closed.setOrganizationId(orgId);
        closed.setWorkflowId(april.getId());
        closed.setVoucherKey("G:apr-1");
        closed.setStatus(AmendmentStatus.DONE);
        closed.setChange(AmendmentChangeKind.CHANGED);
        closed.setTableCode("B2B");
        closed.setOriginalWorkflowId(april.getId());
        closed.setOriginalDocKey("b2b:G:apr-1");
        closed.setDoneBy("acc");
        closed.setDoneAt(Instant.now());
        closed.setDoneNote("manual");
        closed.setCreatedAt(Instant.now());
        closed = followUpRepository.save(closed);
        workflowService.finalize(orgId, "acc", may.getId());
        closed = followUpRepository.findById(closed.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(AmendmentStatus.DONE);
        assertThat(closed.getDoneNote()).isEqualTo("manual");
    }

    // ---------- seeding ----------

    private void setup() throws Exception {
        ownerToken = setupOwnerOrg();
        accountantToken = memberJwt("accountant");
    }

    private Workflow frozenAprilReturn() {
        Workflow april = workflow(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30),
                WorkflowStage.FINALIZED);
        DayBookUploadJob job1 = completedJob(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));
        april.setUploadJobId(job1.getId());
        workflowRepository.save(april);
        seedMasters(orgId, job1.getId());
        seedSalesInvoice(orgId, job1.getId(), "apr-1", "INV-001", LocalDate.of(2025, 4, 15));
        prepareService.buildOrRebuild(april);
        prepareService.freeze(april);
        return april;
    }

    private Workflow mayWorkflow(DayBookUploadJob job) {
        Workflow may = workflow(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31),
                WorkflowStage.PREPARE);
        may.setUploadJobId(job.getId());
        return workflowRepository.save(may);
    }

    private DayBookUploadJob mayJob() {
        DayBookUploadJob job = completedJob(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 31));
        seedMasters(orgId, job.getId());
        return job;
    }

    private Workflow workflow(LocalDate start, LocalDate end, WorkflowStage stage) {
        Workflow w = new Workflow();
        w.setOrganizationId(orgId);
        w.setType(WorkflowType.GSTR1);
        w.setPeriodStart(start);
        w.setPeriodEnd(end);
        w.setPeriodKey(start.toString().substring(0, 7));
        w.setFinancialYear(start.getYear());
        w.setStage(stage);
        w.setActive(true);
        w.setCreatedBy("acc");
        return workflowRepository.save(w);
    }

    private DayBookUploadJob completedJob(LocalDate from, LocalDate to) {
        DayBookUploadJob job = new DayBookUploadJob();
        job.setOrganizationId(orgId);
        job.setFileName("daybook-" + (++counter) + ".json");
        job.setUploadedBy("acc");
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setCoverageFrom(from);
        job.setCoverageTo(to);
        return dayBookUploadJobRepository.save(job);
    }

    private void seedMasters(UUID org, UUID jobId) {
        UploadLedgerSnapshot salesLedger = new UploadLedgerSnapshot();
        salesLedger.setUploadJobId(jobId);
        salesLedger.setOrganizationId(org);
        salesLedger.setLedgerName("Sales Account");
        salesLedger.setHsnSacCode("997331");
        snapshotRepository.save(salesLedger);
        taxMapping(org, "Output CGST", GstHead.CGST);
        taxMapping(org, "Output SGST", GstHead.SGST);
        taxMapping(org, "Output IGST", GstHead.IGST);
        taxMapping(org, "Output Cess", GstHead.CESS);
    }

    private void taxMapping(UUID org, String ledgerName, GstHead head) {
        String key = ledgerName.trim().toLowerCase();
        boolean exists = taxLedgerMappingRepository.findByOrganizationId(org).stream()
                .anyMatch(m -> key.equals(m.getLedgerKey()) && head.equals(m.getHead()));
        if (exists) {
            return;
        }
        com.arktech.superaccountant.gst.mapping.TaxLedgerMapping m =
                new com.arktech.superaccountant.gst.mapping.TaxLedgerMapping();
        m.setOrganizationId(org);
        m.setLedgerName(ledgerName);
        m.setLedgerKey(ledgerName.trim().toLowerCase());
        m.setRole(com.arktech.superaccountant.gst.mapping.TaxLedgerRole.OUTPUT);
        m.setHead(head);
        m.setSavedBy("tester");
        m.setSavedAt(Instant.now());
        taxLedgerMappingRepository.save(m);
    }

    /** Intra-state B2B sales invoice: 10000 taxable @18% + CGST/SGST 900 each. */
    private void seedSalesInvoice(UUID org, UUID jobId, String guid, String number, LocalDate date) {
        ParsedVoucher v = new ParsedVoucher();
        v.setUploadJobId(jobId);
        v.setOrganizationId(org);
        v.setVoucherGuid(guid);
        v.setVoucherTypeName("Sales");
        v.setVoucherNumber(number);
        v.setVoucherDate(date);
        // current parser (DayBookParserService) stamps captureVersion 1
        v.setCaptureVersion(1);
        v.setPartyLedgerName("Sundry Debtors");
        v.setPartyGstin("27AAPFU0939F1ZV");
        v.setPartyGstRegistrationType("Regular");
        v.setPlaceOfSupply("Karnataka");
        v.setTotalDebit(new BigDecimal("11800.00"));
        v.setTotalCredit(new BigDecimal("11800.00"));
        List<ParsedVoucherEntry> entries = new ArrayList<>();
        entries.add(entry(v, "Sales Account", "10000.00", "18", false));
        entries.add(entry(v, "Output CGST", "900.00", null, false));
        entries.add(entry(v, "Output SGST", "900.00", null, false));
        entries.add(entry(v, "Sundry Debtors", "-11800.00", null, true));
        v.setEntries(entries);
        parsedVoucherRepository.save(v);
    }

    private ParsedVoucherEntry entry(ParsedVoucher voucher, String ledger, String amount,
                                     String gstRate, boolean isParty) {
        ParsedVoucherEntry e = new ParsedVoucherEntry();
        e.setParsedVoucher(voucher);
        e.setOrganizationId(voucher.getOrganizationId());
        e.setLedgerName(ledger);
        e.setAmount(new BigDecimal(amount));
        e.setDebit(!isParty);
        if (gstRate != null) {
            e.setGstRate(new BigDecimal(gstRate));
            e.setGstTaxability("Taxable");
        }
        e.setPartyLedger(isParty);
        return e;
    }

    private java.util.Optional<PreparedReturnRow> rowOf(UUID workflowId, String rowKey) {
        PreparedReturn prepared = preparedReturnRepository
                .findByWorkflowIdAndReturnKind(workflowId, "GSTR1").orElseThrow();
        return rowRepository.findByPreparedReturnId(prepared.getId()).stream()
                .filter(r -> r.getRowKey().equals(rowKey))
                .findFirst();
    }

    // ---------- auth helpers (pattern from TamperOutcomesIT) ----------

    private String setupOwnerOrg() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "it_" + suffix;
        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"owner\"}"))
                .andExpect(status().isOk());
        String token = signin(username);
        MvcResult createOrgResult = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"Amendment Flow Org " + suffix + "\",\"financialYearStart\":4}"))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> orgBody = json.readValue(createOrgResult.getResponse().getContentAsString(), Map.class);
        orgId = UUID.fromString(orgBody.get("id").toString());
        Organization org = organizationRepository.findById(orgId).orElseThrow();
        org.setGstin("29ABCDE1234F1Z5");
        org.setBooksStartDate(LocalDate.of(2025, 4, 1));
        organizationRepository.save(org);
        return selectOrg(token);
    }

    private String memberJwt(String role) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "member_" + suffix;
        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@test.com\",\"password\":\"TestPass1!\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk());
        MvcResult inviteResult = mockMvc.perform(post("/api/organizations/" + orgId + "/invites")
                        .param("role", role)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String inviteToken = json.readTree(inviteResult.getResponse().getContentAsString()).get("token").asText();
        mockMvc.perform(post("/api/organizations/invites/" + inviteToken + "/accept")
                        .header("Authorization", "Bearer " + signin(username)))
                .andExpect(status().isOk());
        return selectOrg(signin(username));
    }

    private String signin(String username) throws Exception {
        MvcResult signinResult = mockMvc.perform(post("/api/auth/signin")
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"TestPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (String) json.readValue(signinResult.getResponse().getContentAsString(), Map.class).get("token");
    }

    private String selectOrg(String token) throws Exception {
        MvcResult selectResult = mockMvc.perform(post("/api/organizations/" + orgId + "/select")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return (String) json.readValue(selectResult.getResponse().getContentAsString(), Map.class).get("token");
    }
}