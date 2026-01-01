package com.docflow.document;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DocFormatConverter implements AttributeConverter<DocFormat, Integer> {
    @Override
    public Integer convertToDatabaseColumn(DocFormat attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public DocFormat convertToEntityAttribute(Integer dbData) {
        return DocFormat.fromCode(dbData);
    }
}
