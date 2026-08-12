package sk.automoder.ai;

/**
 * Result of a single AI provider call.
 *
 * @param content   the raw text content returned by the model
 * @param costUsd   the actual cost returned by OpenRouter (USD), 0 if unknown
 * @param latencyMs wall-clock latency of the call in milliseconds
 */
public record AiResult(String content, double costUsd, long latencyMs) {

    public static AiResult empty(long latencyMs) {
        return new AiResult(null, 0.0, latencyMs);
    }
}