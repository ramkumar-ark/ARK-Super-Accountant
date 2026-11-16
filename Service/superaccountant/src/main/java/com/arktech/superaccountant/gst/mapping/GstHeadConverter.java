package com.arktech.superaccountant.gst.mapping;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GstHeadConverter implements AttributeConverter<GstHead, String> {

    @Override
    public String convertToDatabaseColumn(GstHead attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public GstHead convertToEntityAttribute(String dbData) {
        return dbData == null ? null : GstHead.valueOf(dbData);
    }
}
