package sk.automoder.dto;

import java.util.List;
import java.util.Map;

public record ModerationResponse(
        String verdict,
        String severity,
        double risk,
        List<String> categories,
        String reason,
        Long modelId,
        String modelName,
        long latencyMs,
        Long policyId
) {
    private static final Map<String, Integer> SEVERITY_ORDER = Map.of(
            "NONE", 0, "LOW", 1, "MODERATE", 2, "HIGH", 3
    );

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