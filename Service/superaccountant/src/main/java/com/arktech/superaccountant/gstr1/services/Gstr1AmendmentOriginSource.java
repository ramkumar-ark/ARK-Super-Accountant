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

    private static final String RETURN_KIND = "GSTR1";

    @Autowired
    private PreparedReturnRepository preparedReturnRepository;
    @Autowired
    private PreparedReturnRowRepository preparedReturnRowRepository;

    @Override
    public Optional<AmendmentOrigin> origin(UUID workflowId, String voucherKey) {
        if (workflowId == null || voucherKey == null) {
            return Optional.empty();
        }
        Optional<PreparedReturn> prepared =
                preparedReturnRepository.findByWorkflowIdAndReturnKind(workflowId, RETURN_KIND);
        if (prepared.isEmpty()) {
            return Optional.empty();
        }
        return preparedReturnRowRepository.findByPreparedReturnId(prepared.get().getId()).stream()
                .filter(row -> voucherKey.equals(row.getSourceVoucherKey()))
                .findFirst()
                .map(row -> new AmendmentOrigin(row.getTableCode(), row.getRowKey()));
    }
}
