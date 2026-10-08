package sk.automoder.model;

/**
 * What a benchmark run measures.
 */
public enum BenchmarkMode {

    /**
     * Classify each sample into the dataset's own label set (e.g. {@code hate_speech}
     * /{@code offensive}/{@code neither}) and score label accuracy. A policy is not
     * required (it is stored as reference metadata only).
     */
    CLASSIFICATION,

    /**
     * Run the moderation pipeline on each sample: the model rates the content's
     * {@link Severity} against the policy's categories, the app maps it to an
     * {@code ALLOW}/{@code BLOCK} verdict via the policy's {@code thresholdSeverity},
     * and the result is scored against the dataset's expected verdict (derived from
     * {@link Dataset#getLabelVerdicts()}). Requires a policy and a complete
     * label-&gt;verdict mapping on the dataset.
     */
    MODERATION
}
