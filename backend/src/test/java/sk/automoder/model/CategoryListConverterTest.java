package sk.automoder.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link CategoryListConverter}: JSON (de)serialisation of the typed
 * category list, including the legacy bare-string format.
 */
class CategoryListConverterTest {

    private final CategoryListConverter converter = new CategoryListConverter();

    @Test
    void roundTripsNewObjectFormat() {
        List<Category> categories = List.of(
                new Category("hate_speech", "Attacks based on protected attributes."),
                new Category("violence", "Threatens physical violence."));

        String json = converter.convertToDatabaseColumn(categories);
        assertEquals(categories, converter.convertToEntityAttribute(json));
    }

    @Test
    void readsLegacyBareStringFormat() {
        List<Category> categories = converter.convertToEntityAttribute("[\"hate_speech\",\"violence\"]");

        assertEquals(List.of(
                new Category("hate_speech", "hate_speech"),
                new Category("violence", "violence")), categories);
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
        assertTrue(converter.convertToEntityAttribute("{\"not\":\"an array\"}").isEmpty());
    }
}
