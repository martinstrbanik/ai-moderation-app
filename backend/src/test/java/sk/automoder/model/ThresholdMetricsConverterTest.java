package sk.automoder.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ThresholdMetricsConverter}: JSON (de)serialisation of the
 * per-threshold metrics map (the moderation benchmark sweep).
 */
class ThresholdMetricsConverterTest {

    private final ThresholdMetricsConverter converter = new ThresholdMetricsConverter();

    @Test
    void roundTripsMapWithEnumKeys() {
        Map<Severity, MetricScores> metrics = new LinkedHashMap<>();
        metrics.put(Severity.NONE, new MetricScores(0.4, 1.0, 0.571, 0.4));
        metrics.put(Severity.MODERATE, new MetricScores(0.9, 0.8, 0.847, 0.85));

        String json = converter.convertToDatabaseColumn(metrics);

        Map<Severity, MetricScores> parsed = converter.convertToEntityAttribute(json);
        assertEquals(metrics, parsed);
    }

    @Test
    void emptyAndNullBecomeEmptyMapOrEmptyObject() {
        assertEquals("{}", converter.convertToDatabaseColumn(null));
        assertEquals("{}", converter.convertToDatabaseColumn(Map.of()));
        assertTrue(converter.convertToEntityAttribute(null).isEmpty());
        assertTrue(converter.convertToEntityAttribute("   ").isEmpty());
    }

    @Test
    void unparseableContentYieldsEmptyMap() {
        assertTrue(converter.convertToEntityAttribute("not json").isEmpty());
    }
}
