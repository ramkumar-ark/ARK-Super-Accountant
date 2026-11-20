package com.arktech.superaccountant.workflow.snapshot;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AmendmentChangeKindConverter implements AttributeConverter<AmendmentChangeKind, String> {
    @Override
    public String convertToDatabaseColumn(AmendmentChangeKind attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public AmendmentChangeKind convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AmendmentChangeKind.valueOf(dbData);
    }
}
