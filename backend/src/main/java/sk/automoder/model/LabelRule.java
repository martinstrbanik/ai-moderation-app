package sk.automoder.model;

/**
 * Maps a dataset label to the moderation verdict it implies ({@link PolicyAction#ALLOW}
 * or {@link PolicyAction#BLOCK}).
 *
 * <p>Datasets only carry a categorical ground-truth label, not a verdict, so a
 * <b>moderation</b> benchmark needs this per-dataset mapping to turn each expected
 * label into the expected {@code ALLOW}/{@code BLOCK} decision. The user marks the
 * labels that should be considered blocked.</p>
 *
 * <p>This is a value type, not a JPA entity. It is persisted as a JSON array inside
 * {@link Dataset#getLabelVerdicts()} via {@link LabelRuleListConverter}.</p>
 */
public record LabelRule(String label, PolicyAction verdict) {
}
