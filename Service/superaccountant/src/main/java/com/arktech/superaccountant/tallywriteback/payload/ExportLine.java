package com.arktech.superaccountant.tallywriteback.payload;

import com.arktech.superaccountant.tallywriteback.models.TallyChangeType;
import com.arktech.superaccountant.tallywriteback.models.TallyField;

public record ExportLine(
        TallyChangeType changeType,
        TallyField field,
        String ledgerName,
        String value) {
}
