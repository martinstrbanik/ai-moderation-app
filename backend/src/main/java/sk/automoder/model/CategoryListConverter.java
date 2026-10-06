package sk.automoder.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA converter persisting {@code List<Category>} as a JSON array in a {@code text}
 * column. Uses Jackson so the entity can expose a typed list instead of a raw JSON
 * string - no manual parsing in the service layer.
 *
 * <p>Reading is tolerant of the <b>legacy</b> format where categories were stored as
 * a plain array of strings ({@code ["hate_speech"]}): such a value is read as a
 * category whose {@code id} and {@code prompt} are both the string. This keeps
 * pre-existing rows working without a data migration.</p>
 */
@Converter
public class CategoryListConverter implements AttributeConverter<List<Category>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Category>> CATEGORY_LIST = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<Category> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize categories to JSON.", e);
        }
    }

    @Override
    public List<Category> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(dbData);
            if (!root.isArray()) {
                return new ArrayList<>();
            }
            List<Category> out = new ArrayList<>();
            for (JsonNode node : root) {
                if (node.isTextual()) {
                    // legacy form: a bare label string -> id = prompt = the string
                    String label = node.asText();
                    out.add(new Category(label, label));
                } else {
                    out.add(OBJECT_MAPPER.treeToValue(node, Category.class));
                }
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
