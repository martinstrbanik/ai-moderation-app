package sk.automoder.ai;

import sk.automoder.model.Category;

import java.util.List;

/**
 * Builds prompts for moderation and benchmark classification.
 * All prompts request strict JSON output (OpenRouter response_format = json_object).
 */
public final class PromptFactory {

    private PromptFactory() {
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
     * a final verdict. The application maps severity to a verdict (BLOCK / ALLOW).
     *
     * <p>Categories are presented as a numbered list of mini-prompts; the model reports
     * the hit categories by their <b>1-based number</b> (never by id or a free-form label),
     * so it can only ever reference categories supplied by the policy.</p>
     */
    public static String severitySystemPrompt(List<Category> categories) {
        return "You are a content moderation assistant. "
                + categoryBlock(categories)
                + "Rate the severity of the content on this scale:\n"
                + "- NONE (safe, no violation)\n"
                + "- LOW (mild concern, borderline)\n"
                + "- MODERATE (clear violation)\n"
                + "- HIGH (severe violation)\n"
                + "In \"categories\", list the 1-based NUMBERS of the categories the content falls into, "
                + "using ONLY numbers from the list above (empty array if none). Never invent categories.\n"
                + "Return ONLY a JSON object with these fields:\n"
                + "{\"severity\": \"NONE|LOW|MODERATE|HIGH\", \"categories\": [1,2], \"reason\": \"...\"}.";
    }

    /**
     * Severity-based moderation prompt for a batch of texts - returns a JSON array
     * of {@code {"id": n, "severity": "...", "categories": [1,2], "reason": "..."}}
     * objects, one per input text, in exactly the same order. The {@code id} is the
     * 1-based number of the text as produced by {@link #batchUserContent(List)};
     * {@code categories} holds the 1-based numbers of the hit categories.
     */
    public static String severityBatchSystemPrompt(List<Category> categories) {
        return "You are a content moderation assistant. "
                + categoryBlock(categories)
                + "The user provides numbered texts. For EVERY text rate its severity on this scale:\n"
                + "- NONE (safe, no violation)\n"
                + "- LOW (mild concern, borderline)\n"
                + "- MODERATE (clear violation)\n"
                + "- HIGH (severe violation)\n"
                + "In \"categories\", list the 1-based NUMBERS of the categories the text falls into, "
                + "using ONLY numbers from the list above (empty array if none). Never invent categories.\n"
                + "Return ONLY a JSON array of objects, one per input text, in exactly the same order, "
                + "each object in the form "
                + "{\"id\": <number from 1>, \"severity\": \"NONE|LOW|MODERATE|HIGH\", "
                + "\"categories\": [1,2], \"reason\": \"...\"}. "
                + "Do not omit or add any text.";
    }

    /**
     * Renders the numbered category block (mini-prompt text only, never the ids). When
     * there are no categories the block is empty and the model is asked to return an
     * empty category list.
     */
    private static String categoryBlock(List<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return "The policy defines no categories, so always return an empty \"categories\" array. ";
        }
        StringBuilder sb = new StringBuilder(
                "The policy defines the following categories. Consider ONLY these and refer to them "
                        + "by their number; never invent new categories:\n");
        for (int i = 0; i < categories.size(); i++) {
            sb.append(i + 1).append(". ").append(categories.get(i).prompt()).append('\n');
        }
        return sb.toString();
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