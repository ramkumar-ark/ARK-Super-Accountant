package com.arktech.superaccountant.filedreturns.recon;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class DiscrepancyKindConverter implements AttributeConverter<DiscrepancyKind, String> {
    @Override
    public String convertToDatabaseColumn(DiscrepancyKind attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public DiscrepancyKind convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DiscrepancyKind.valueOf(dbData);
    }
}
