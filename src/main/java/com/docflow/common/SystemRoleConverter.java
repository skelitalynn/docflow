package com.docflow.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SystemRoleConverter implements AttributeConverter<SystemRole, Integer> {
    @Override
    public Integer convertToDatabaseColumn(SystemRole attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public SystemRole convertToEntityAttribute(Integer dbData) {
        return SystemRole.fromCode(dbData);
    }
}
