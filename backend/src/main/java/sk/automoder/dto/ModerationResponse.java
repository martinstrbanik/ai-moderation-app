package sk.automoder.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of a batched moderation request: the moderated texts grouped by verdict
 * (ALLOW / FLAG / BLOCK) plus request-level metadata and aggregates.
 *
 * <p>Items that could not be classified by the model are placed in the {@code flag}
 * list with {@code severity = "UNKNOWN"} and the error in {@code reason} (FLAG means
 * "needs human review", which is exactly the case for an unclassified text).</p>
 */
public record ModerationResponse(
        List<ModerationResultItem> allow,
        List<ModerationResultItem> flag,
        List<ModerationResultItem> block,

        // policy context
        Long policyId,
        String policyName,
        Double threshold,
        String action,

        // model context
        Long modelId,
        String modelName,
        boolean usedFallback,

        // request / batch info
        String requestId,
        Integer batchSize,
        int batchCount,
        int totalItems,

        // aggregates
        Map<String, Integer> verdictCounts,
        Map<String, Integer> severityCounts,
        Map<String, Integer> categoryCounts,

        // timing / cost
        long latencyMs,
        double cost,
        double avgLatencyPerItem,
        Instant timestamp
) {

    private static final Map<String, Integer> SEVERITY_ORDER = Map.of(
            "NONE", 0, "LOW", 1, "MODERATE", 2, "HIGH", 3
    );

    /**
     * A single moderated text.
     *
     * @param id         application-assigned <b>internal</b> id (1-based position in the request)
     * @param externalId the caller's external id, echoed back
     * @param latencyMs  the <b>whole batch's</b> latency attributed to this item (not measured
     *                   individually); timing of batched calls cannot be split per text
     * @param cost       this item's share of its batch's OpenRouter cost
     *                   ({@code batchCost / batchSize}); likewise an attribution, not a
     *                   per-text measurement
     */
    public record ModerationResultItem(
            Long id,
            String externalId,
            String text,
            String verdict,
            String severity,
            double risk,
            List<String> categories,
            String reason,
            long latencyMs,
            double cost
    ) {
    }

    public static double riskFromSeverity(String severity) {
        int ord = SEVERITY_ORDER.getOrDefault(severity.toUpperCase(), 0);
        return switch (ord) {
            case 0 -> 0.0;
            case 1 -> 1.0 / 3.0;
            case 2 -> 2.0 / 3.0;
            case 3 -> 1.0;
            default -> 0.0;
        };
    }
}