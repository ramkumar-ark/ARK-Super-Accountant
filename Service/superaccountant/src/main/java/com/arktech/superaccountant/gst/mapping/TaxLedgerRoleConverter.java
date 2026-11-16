package com.arktech.superaccountant.gst.mapping;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TaxLedgerRoleConverter implements AttributeConverter<TaxLedgerRole, String> {

    @Override
    public String convertToDatabaseColumn(TaxLedgerRole attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TaxLedgerRole convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TaxLedgerRole.valueOf(dbData);
    }
}
