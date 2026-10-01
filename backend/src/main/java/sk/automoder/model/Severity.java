package sk.automoder.model;

/**
 * Severity of a piece of content, as rated by the model.
 *
 * <p>The scale {@code NONE < LOW < MODERATE < HIGH} is shared by two things:</p>
 * <ul>
 *   <li>the model's per-text severity rating, and</li>
 *   <li>the policy threshold ({@code thresholdSeverity}) — the <b>minimum</b> severity
 *       that triggers the policy action.</li>
 * </ul>
 *
 * <p>{@link #UNKNOWN} is an application-side sentinel for a text the model could not
 * classify (such texts become {@code FLAG}). It is <b>never</b> a valid threshold and
 * must not be compared with {@link #atLeast(Severity)} for verdict mapping.</p>
 */
public enum Severity {

    NONE,
    LOW,
    MODERATE,
    HIGH,
    UNKNOWN;

    /**
     * @return {@code true} if this severity is at least {@code other} on the
     *         {@code NONE < LOW < MODERATE < HIGH} scale. {@link #UNKNOWN} is not
     *         part of the ordered scale and always returns {@code false}.
     */
    public boolean atLeast(Severity other) {
        if (this == UNKNOWN || other == UNKNOWN) {
            return false;
        }
        return this.ordinal() >= other.ordinal();
    }
}
