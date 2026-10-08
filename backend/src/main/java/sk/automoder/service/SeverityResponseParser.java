package sk.automoder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import sk.automoder.model.Severity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared, Spring-free parsing of the severity the model reports in the moderation /
 * severity prompts. Extracted so both {@link ModerationService} and the moderation
 * benchmark ({@code BenchmarkExecutor}) apply the exact same rules.
 *
 * <p>Keeping this pure (no Spring, no IO) keeps it directly unit-testable, like
 * {@link ModerationMapping}.</p>
 */
public final class SeverityResponseParser {

    private SeverityResponseParser() {
    }

    /**
     * Parses a raw severity string into the enum, defaulting to {@link Severity#NONE}
     * on {@code null} or unrecognised values (matching the moderation behaviour).
     */
    public static Severity parseSeverity(String raw) {
        if (raw == null) {
            return Severity.NONE;
        }
        try {
            return Severity.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Severity.NONE;
        }
    }

    /**
     * Parses a severity-prompt response into a map of <b>1-based batch position</b>
     * -&gt; {@link Severity}. Accepts a JSON array (batch mode, keyed by the returned
     * {@code id}) or a single JSON object (single mode, position 1).
     *
     * <p>Positions absent from the response are simply not present in the map, so the
     * caller can treat them as errors. An unusable response yields an empty map (which
     * the caller uses to trigger the individual-call fallback).</p>
     *
     * @param batchSize number of texts in the batch (bounds the accepted ids)
     */
    public static Map<Integer, Severity> parseBatchSeverities(ObjectMapper objectMapper,
                                                              String content, int batchSize) {
        Map<Integer, Severity> out = new LinkedHashMap<>();
        if (content == null) {
            return out;
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    int id = node.path("id").asInt(-1);
                    if (id >= 1 && id <= batchSize) {
                        out.put(id, parseSeverity(node.path("severity").asText("NONE")));
                    }
                }
            } else if (root.isObject() && batchSize == 1) {
                out.put(1, parseSeverity(root.path("severity").asText("NONE")));
            }
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
        return out;
    }
}
