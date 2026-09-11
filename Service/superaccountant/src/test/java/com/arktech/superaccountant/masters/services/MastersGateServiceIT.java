package com.arktech.superaccountant.masters.services;

import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.GateResult;
import com.arktech.superaccountant.masters.models.LedgerCategory;
import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.masters.models.UploadJob;
import com.arktech.superaccountant.masters.models.UploadJobStatus;
import com.arktech.superaccountant.masters.models.ValidationFinding;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import com.arktech.superaccountant.masters.repository.UploadJobRepository;
import com.arktech.superaccountant.masters.repository.ValidationFindingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MastersGateServiceIT {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UploadJobRepository uploadJobRepository;

    @Autowired
    private ValidationFindingRepository findingRepository;

    @Autowired
    private MastersGateService gateService;

    @Test
    void checkGate_withPersistedMediumOpenTdsFinding_returnsGated() {
        Organization org = organizationRepository.save(new Organization("Gate Query Org"));

        UploadJob job = new UploadJob();
        job.setOrganizationId(org.getId());
        job.setFileName("masters.xml");
        job.setStatus(UploadJobStatus.COMPLETED);
        job.setTotalLedgersParsed(12);
        job = uploadJobRepository.save(job);

        ValidationFinding finding = new ValidationFinding();
        finding.setUploadJobId(job.getId());
        finding.setRuleCode("TDS_SECTION_MAPPING");
        finding.setCategory(LedgerCategory.EXPENSE);
        finding.setLedgerName("Professional Fees");
        finding.setSeverity(FindingSeverity.MEDIUM);
        finding.setResolveStatus(ResolveStatus.OPEN);
        finding.setMessage("Missing TDS section mapping");
        findingRepository.saveAndFlush(finding);

        assertThat(findingRepository.countUnresolvedForGate(job.getId())).isEqualTo(1L);

        GateResult result = gateService.checkGate(org.getId());

        assertThat(result.gated()).isTrue();
        assertThat(result.unresolvedCount()).isEqualTo(1);
    }
}
