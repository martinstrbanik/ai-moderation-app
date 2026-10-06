package sk.automoder.model;

/**
 * A policy category expressed as a <b>mini-prompt</b>.
 *
 * <p>The {@code prompt} is the only part shown to the model: it describes what the
 * category covers so the model can decide whether a text falls inside it. The
 * {@code id} is a short, stable, machine-safe slug used purely for reference
 * (matching the model's answer back to the category, logging and display) and is
 * never sent to the model.</p>
 *
 * <p>This is a value type, not a JPA entity. It is persisted as JSON inside
 * {@link Policy#getCategories()} via {@link CategoryListConverter}.</p>
 */
public record Category(String id, String prompt) {
}
