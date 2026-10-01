package sk.automoder.service;

import sk.automoder.dto.ModerationResponse.ModerationResultItem;
import sk.automoder.model.PolicyAction;
import sk.automoder.model.Severity;

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

    private ModerationMapping() {
    }

    /**
     * Maps a model severity to a verdict using the policy threshold: if the rated
     * severity is at least {@code threshold} (the minimum severity that triggers the
     * action) the policy {@code action} is returned, otherwise {@link PolicyAction#ALLOW}.
     *
     * <p>{@link Severity#UNKNOWN} (unclassified) never triggers the action here; the
     * service places such items in {@code FLAG} explicitly.</p>
     */
    public static PolicyAction mapVerdict(Severity threshold, PolicyAction action, Severity severity) {
        if (severity == null || threshold == null) {
            return PolicyAction.ALLOW;
        }
        return severity.atLeast(threshold) ? action : PolicyAction.ALLOW;
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
        for (Severity level : Severity.values()) {
            out.put(level.name(), 0);
        }
        for (ModerationResultItem item : items) {
            String level = item.severity() == null ? Severity.UNKNOWN.name() : item.severity().name();
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
