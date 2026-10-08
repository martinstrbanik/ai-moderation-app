package sk.automoder.model;

/**
 * Macro-averaged classification metrics for a single severity threshold
 * (precision / recall / F1 / accuracy). Used by the moderation benchmark's
 * threshold sweep: the same severity ratings yield the verdict metrics for every
 * threshold without extra model calls.
 *
 * <p>This is a value type, not a JPA entity. It is persisted as part of a JSON map
 * inside {@link BenchmarkResult#getThresholdMetrics()} via {@link ThresholdMetricsConverter}.</p>
 */
public record MetricScores(double precision, double recall, double f1, double accuracy) {
}
