package com.arktech.superaccountant.gstr1.services;

import com.arktech.superaccountant.gstr1.models.PreparedReturn;
import com.arktech.superaccountant.gstr1.models.PreparedReturnRow;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRepository;
import com.arktech.superaccountant.gstr1.repository.PreparedReturnRowRepository;
import com.arktech.superaccountant.workflow.snapshot.AmendmentOriginSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the GSTR-1 prepared-return origin (table + row key) of a tampered
 * voucher for an affected workflow's frozen prepared return.
 */
@Component
public class Gstr1AmendmentOriginSource implements AmendmentOriginSource {

    @Autowired
    private PreparedReturnRepository preparedReturnRepository;
    @Autowired
    private PreparedReturnRowRepository preparedReturnRowRepository;
    @Autowired
    private com.arktech.superaccountant.workflow.repository.WorkflowRepository workflowRepository;

    @Override
    public Optional<AmendmentOrigin> origin(UUID workflowId, String voucherKey) {
        if (workflowId == null || voucherKey == null) {
            return Optional.empty();
        }
        // T22: the workflow may be an IFF (returnKind IFF), not only a GSTR1 one
        Optional<PreparedReturn> prepared = workflowRepository.findById(workflowId).flatMap(
                workflow -> preparedReturnRepository.findByWorkflowIdAndReturnKind(workflowId,
                        Gstr1PrepareService.returnKind(workflow)));
        if (prepared.isEmpty()) {
            return Optional.empty();
        }
        return preparedReturnRowRepository.findByPreparedReturnId(prepared.get().getId()).stream()
                .filter(row -> voucherKey.equals(row.getSourceVoucherKey()))
                .findFirst()
                .map(row -> new AmendmentOrigin(row.getTableCode(), row.getRowKey()));
    }
}
