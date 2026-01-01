package com.docflow.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CommentStatusConverter implements AttributeConverter<CommentStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(CommentStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public CommentStatus convertToEntityAttribute(Integer dbData) {
        return CommentStatus.fromCode(dbData);
    }
}
