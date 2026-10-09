package com.example.ragingestor.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

@Converter
public class TagListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> tags) {
        return tags == null ? "" : String.join(",", tags);
    }

    @Override
    public List<String> convertToEntityAttribute(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split(",", -1));
    }
}
