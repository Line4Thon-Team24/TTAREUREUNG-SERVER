package com.ttareureung.api.domain.senior.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

// hibernate.jdbc.time_zone=UTC가 TIME 컬럼까지 9시간 밀어 저장하므로 "HH:mm" 문자열로 보관
@Converter
public class HourMinuteConverter implements AttributeConverter<LocalTime, String> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String convertToDatabaseColumn(LocalTime attribute) {
        return attribute == null ? null : attribute.format(FORMAT);
    }

    @Override
    public LocalTime convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LocalTime.parse(dbData, FORMAT);
    }
}
