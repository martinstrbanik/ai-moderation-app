package sk.automoder.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA converter persisting {@code List<LabelRule>} as a JSON array in a {@code text}
 * column, so the entity can expose a typed list instead of a raw JSON string.
 *
 * <p>Mirrors {@link CategoryListConverter}: reads are tolerant - unparseable or
 * non-array content yields an empty list rather than failing.</p>
 */
@Converter
public class LabelRuleListConverter implements AttributeConverter<List<LabelRule>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<LabelRule>> RULE_LIST = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<LabelRule> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize label verdicts to JSON.", e);
        }
    }

    @Override
    public List<LabelRule> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, RULE_LIST);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
