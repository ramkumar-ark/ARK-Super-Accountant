package com.arktech.superaccountant.tallywriteback.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ExistenceOutcomeConverter implements AttributeConverter<ExistenceOutcome, String> {
    @Override
    public String convertToDatabaseColumn(ExistenceOutcome attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public ExistenceOutcome convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ExistenceOutcome.valueOf(dbData);
    }
}
