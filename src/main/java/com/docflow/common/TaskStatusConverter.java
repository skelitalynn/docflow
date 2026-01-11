package com.docflow.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TaskStatusConverter implements AttributeConverter<TaskStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(TaskStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public TaskStatus convertToEntityAttribute(Integer dbData) {
        return TaskStatus.fromCode(dbData);
    }
}
