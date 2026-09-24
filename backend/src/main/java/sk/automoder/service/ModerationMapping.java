package sk.automoder.service;

import sk.automoder.dto.ModerationResponse.ModerationResultItem;
import sk.automoder.model.PolicyAction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure helpers for the moderation module: mapping model severity to a policy
 * verdict and aggregating/grouping per-item results. Kept free of Spring/IO so
 * it can be unit tested directly.
 */
public final class ModerationMapping {

    /** Severity used for texts the model could not classify; such items become FLAG. */
    public static final String SEVERITY_UNKNOWN = "UNKNOWN";

    private static final List<String> SEVERITY_LEVELS =
            List.of("NONE", "LOW", "MODERATE", "HIGH", SEVERITY_UNKNOWN);

    private static final Map<String, Integer> SEVERITY_ORDER = Map.of(
            "NONE", 0, "LOW", 1, "MODERATE", 2, "HIGH", 3
    );

    private ModerationMapping() {
    }

    /**
     * Maps a model severity to a verdict using the policy threshold:
     * {@code severityOrdinal >= floor(threshold * 4)} → {@code action}, else {@code ALLOW}
     * (ordinals NONE=0, LOW=1, MODERATE=2, HIGH=3).
     */
    public static PolicyAction mapVerdict(double threshold, PolicyAction action, String severity) {
        int sevOrd = SEVERITY_ORDER.getOrDefault(severity == null ? "" : severity.toUpperCase(), 0);
        int threshOrd = (int) Math.floor(threshold * 4);
        if (threshOrd > 3) {
            threshOrd = 3;
        }
        return sevOrd >= threshOrd ? action : PolicyAction.ALLOW;
    }

    /**
     * Groups results by verdict. The map always contains an (possibly empty) list for
     * every {@link PolicyAction}, in enum order (ALLOW, FLAG, BLOCK).
     */
    public static Map<String, List<ModerationResultItem>> groupByVerdict(List<ModerationResultItem> items) {
        Map<String, List<ModerationResultItem>> out = new LinkedHashMap<>();
        for (PolicyAction action : PolicyAction.values()) {
            out.put(action.name(), new ArrayList<>());
        }
        for (ModerationResultItem item : items) {
            out.computeIfAbsent(item.verdict(), k -> new ArrayList<>()).add(item);
        }
        return out;
    }

    /** Counts results per verdict; always contains every verdict (0 when absent). */
    public static Map<String, Integer> verdictCounts(List<ModerationResultItem> items) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (PolicyAction action : PolicyAction.values()) {
            out.put(action.name(), 0);
        }
        for (ModerationResultItem item : items) {
            out.merge(item.verdict(), 1, Integer::sum);
        }
        return out;
    }

    /** Counts results per severity, ordered NONE/LOW/MODERATE/HIGH/UNKNOWN (0 when absent). */
    public static Map<String, Integer> severityCounts(List<ModerationResultItem> items) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String level : SEVERITY_LEVELS) {
            out.put(level, 0);
        }
        for (ModerationResultItem item : items) {
            String level = item.severity() == null ? SEVERITY_UNKNOWN : item.severity().toUpperCase();
            out.merge(level, 1, Integer::sum);
        }
        return out;
    }

    /** Counts how often each category was reported, ordered alphabetically. */
    public static Map<String, Integer> categoryCounts(List<ModerationResultItem> items) {
        Map<String, Integer> out = new TreeMap<>();
        for (ModerationResultItem item : items) {
            if (item.categories() == null) {
                continue;
            }
            for (String category : item.categories()) {
                out.merge(category, 1, Integer::sum);
            }
        }
        return out;
    }
}
