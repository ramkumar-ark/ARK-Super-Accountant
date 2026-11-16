package com.arktech.superaccountant.filedreturns.recon;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class DiscrepancyGrainConverter implements AttributeConverter<DiscrepancyGrain, String> {
    @Override
    public String convertToDatabaseColumn(DiscrepancyGrain attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public DiscrepancyGrain convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DiscrepancyGrain.valueOf(dbData);
    }
}
