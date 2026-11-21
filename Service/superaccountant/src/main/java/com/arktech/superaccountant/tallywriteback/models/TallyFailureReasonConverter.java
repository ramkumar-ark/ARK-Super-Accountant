package com.arktech.superaccountant.tallywriteback.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TallyFailureReasonConverter implements AttributeConverter<TallyFailureReason, String> {
    @Override
    public String convertToDatabaseColumn(TallyFailureReason attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TallyFailureReason convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TallyFailureReason.valueOf(dbData);
    }
}
