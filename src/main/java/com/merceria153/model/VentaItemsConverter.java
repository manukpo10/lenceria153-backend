package com.merceria153.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class VentaItemsConverter implements AttributeConverter<List<Venta.VentaItem>, String> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Venta.VentaItem> items) {
        if (items == null || items.isEmpty()) return "[]";
        try {
            return mapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Venta.VentaItem> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<List<Venta.VentaItem>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}