package com.docflow.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MeetingStatusConverter implements AttributeConverter<MeetingStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(MeetingStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public MeetingStatus convertToEntityAttribute(Integer dbData) {
        return MeetingStatus.fromCode(dbData);
    }
}
