package com.docflow.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NotificationTypeConverter implements AttributeConverter<NotificationType, Integer> {
    @Override
    public Integer convertToDatabaseColumn(NotificationType attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public NotificationType convertToEntityAttribute(Integer dbData) {
        return NotificationType.fromCode(dbData);
    }
}
