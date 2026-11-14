package com.arktech.superaccountant.gst.models;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class GstFindingTypeConverter implements AttributeConverter<GstFindingType, String> {
    @Override
    public String convertToDatabaseColumn(GstFindingType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public GstFindingType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : GstFindingType.valueOf(dbData);
    }
}
