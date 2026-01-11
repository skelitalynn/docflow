package com.docflow.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserStatusConverter implements AttributeConverter<UserStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(UserStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public UserStatus convertToEntityAttribute(Integer dbData) {
        return UserStatus.fromCode(dbData);
    }
}
