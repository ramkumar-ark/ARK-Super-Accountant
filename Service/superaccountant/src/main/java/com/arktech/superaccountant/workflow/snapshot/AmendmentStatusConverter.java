package com.arktech.superaccountant.workflow.snapshot;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AmendmentStatusConverter implements AttributeConverter<AmendmentStatus, String> {
    @Override
    public String convertToDatabaseColumn(AmendmentStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public AmendmentStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AmendmentStatus.valueOf(dbData);
    }
}
