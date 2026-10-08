package sk.automoder.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JPA converter persisting a {@code Map<Severity, MetricScores>} as a JSON object in a
 * {@code text} column, so the entity can expose the moderation benchmark's per-threshold
 * metrics (the threshold sweep) as a typed map.
 *
 * <p>Severity enum keys are serialized as their names (e.g. {@code "MODERATE"}).</p>
 */
@Converter
public class ThresholdMetricsConverter implements AttributeConverter<Map<Severity, MetricScores>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<Severity, MetricScores>> METRICS_MAP = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<Severity, MetricScores> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize threshold metrics to JSON.", e);
        }
    }

    @Override
    public Map<Severity, MetricScores> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, METRICS_MAP);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
