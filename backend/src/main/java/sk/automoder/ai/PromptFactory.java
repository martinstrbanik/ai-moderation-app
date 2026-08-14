package sk.automoder.ai;

import java.util.List;

/**
 * Builds prompts for moderation and benchmark classification.
 * All prompts request strict JSON output (OpenRouter response_format = json_object).
 */
public final class PromptFactory {

    private PromptFactory() {
    }

    /**
     * Moderation prompt - classifies content according to a policy.
     */
    public static String moderationSystemPrompt(String policyName, List<String> categories, String action, double threshold) {
        return "You are a content moderation assistant for the policy \"" + policyName + "\". "
                + "Relevant categories: " + (categories.isEmpty() ? "general" : String.join(", ", categories)) + ". "
                + "The moderation action must be one of ALLOW, FLAG, BLOCK. "
                + "Threshold for blocking: " + threshold + ". "
                + "Return ONLY a JSON object with these fields: "
                + "{\"verdict\": \"ALLOW|FLAG|BLOCK\", \"categories\": [\"...\"], \"confidence\": 0.0-1.0, \"reason\": \"...\"}.";
    }

    /**
     * Benchmark classification prompt - classifies content into the dataset label set.
     */
    public static String classificationSystemPrompt(List<String> labels) {
        return "You are a text classifier. Classify the text into exactly one of these labels: "
                + String.join(", ", labels) + ". "
                + "Return ONLY a JSON object in the form {\"label\": \"<one of the labels>\"}.";
    }

    /**
     * Severity-based moderation prompt - asks the model to rate severity on
     * a predefined scale (NONE / LOW / MODERATE / HIGH) instead of returning
     * a final verdict. The application maps severity to the policy action.
     */
    public static String severitySystemPrompt(String policyName, List<String> categories, String action, double threshold) {
        return "You are a content moderation assistant for the policy \"" + policyName + "\". "
                + "Relevant categories: " + (categories.isEmpty() ? "general" : String.join(", ", categories)) + ". "
                + "Rate the severity of the content on this scale:\n"
                + "- NONE (safe, no violation)\n"
                + "- LOW (mild concern, borderline)\n"
                + "- MODERATE (clear violation)\n"
                + "- HIGH (severe violation)\n"
                + "The policy action for violations is: " + action + ".\n"
                + "Return ONLY a JSON object with these fields:\n"
                + "{\"severity\": \"NONE|LOW|MODERATE|HIGH\", \"categories\": [\"...\"], \"reason\": \"...\"}.";
    }

    public static String userContent(String text) {
        return text;
    }

    /**
     * Benchmark classification prompt for a batch of texts - returns a JSON
     * array of {@code {"id": n, "label": "..."}} objects in the same order.
     */
    public static String classificationBatchSystemPrompt(List<String> labels) {
        return "You are a text classifier. The user provides numbered texts. "
                + "For EVERY text classify it into exactly one of these labels: "
                + String.join(", ", labels) + ". "
                + "Return ONLY a JSON array of objects, one per input text, in exactly the same order, "
                + "each object in the form {\"id\": <number from 1>, \"label\": \"<one of the labels>\"}. "
                + "Do not omit or add any text.";
    }

    /** Builds the numbered batch user content ("1. text\n2. text\n..."). */
    public static String batchUserContent(List<String> texts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < texts.size(); i++) {
            sb.append(i + 1).append(". ").append(texts.get(i)).append('\n');
        }
        return sb.toString().trim();
    }
}