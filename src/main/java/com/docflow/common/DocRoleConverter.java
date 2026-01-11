package com.docflow.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DocRoleConverter implements AttributeConverter<DocRole, Integer> {
    @Override
    public Integer convertToDatabaseColumn(DocRole attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public DocRole convertToEntityAttribute(Integer dbData) {
        return DocRole.fromCode(dbData);
    }
}
