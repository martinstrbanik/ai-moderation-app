package sk.automoder.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LabelRuleListConverter}: JSON (de)serialisation of the typed
 * label -&gt; verdict list.
 */
class LabelRuleListConverterTest {

    private final LabelRuleListConverter converter = new LabelRuleListConverter();

    @Test
    void roundTripsRules() {
        List<LabelRule> rules = List.of(
                new LabelRule("hate_speech", PolicyAction.BLOCK),
                new LabelRule("neither", PolicyAction.ALLOW));

        String json = converter.convertToDatabaseColumn(rules);
        assertEquals(rules, converter.convertToEntityAttribute(json));
    }

    @Test
    void emptyAndNullBecomeEmptyListOrEmptyArray() {
        assertEquals("[]", converter.convertToDatabaseColumn(null));
        assertEquals("[]", converter.convertToDatabaseColumn(List.of()));
        assertTrue(converter.convertToEntityAttribute(null).isEmpty());
        assertTrue(converter.convertToEntityAttribute("").isEmpty());
        assertTrue(converter.convertToEntityAttribute("   ").isEmpty());
    }

    @Test
    void unparseableContentYieldsEmptyList() {
        assertTrue(converter.convertToEntityAttribute("not json").isEmpty());
    }
}
