package sk.automoder.service;

import org.junit.jupiter.api.Test;
import sk.automoder.dto.ModerationResponse.ModerationResultItem;
import sk.automoder.model.PolicyAction;
import sk.automoder.model.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static sk.automoder.dto.ModerationResponse.riskFromSeverity;

class ModerationMappingTest {

    private static final double EPS = 1e-9;

    // ---------- verdict mapping ----------

    @Test
    void moderateThresholdBlocksOnModerateAndHigh() {
        assertEquals(PolicyAction.ALLOW, ModerationMapping.mapVerdict(Severity.MODERATE, Severity.NONE));
        assertEquals(PolicyAction.ALLOW, ModerationMapping.mapVerdict(Severity.MODERATE, Severity.LOW));
        assertEquals(PolicyAction.BLOCK, ModerationMapping.mapVerdict(Severity.MODERATE, Severity.MODERATE));
        assertEquals(PolicyAction.BLOCK, ModerationMapping.mapVerdict(Severity.MODERATE, Severity.HIGH));
    }

    @Test
    void highThresholdOnlyBlocksOnHigh() {
        assertEquals(PolicyAction.ALLOW, ModerationMapping.mapVerdict(Severity.HIGH, Severity.MODERATE));
        assertEquals(PolicyAction.BLOCK, ModerationMapping.mapVerdict(Severity.HIGH, Severity.HIGH));
    }

    @Test
    void noneThresholdAlwaysBlocks() {
        assertEquals(PolicyAction.BLOCK, ModerationMapping.mapVerdict(Severity.NONE, Severity.NONE));
    }

    @Test
    void unknownSeverityFallsBackToAllow() {
        // mapVerdict alone treats unknown severity like NONE; the service routes
        // unclassified items to the error list explicitly.
        assertEquals(PolicyAction.ALLOW, ModerationMapping.mapVerdict(Severity.MODERATE, Severity.UNKNOWN));
    }

    // ---------- grouping & counts ----------

    private static ModerationResultItem item(long id, String verdict, Severity severity, List<String> categories) {
        return new ModerationResultItem(id, "ext-" + id, "text " + id, verdict, severity,
                riskFromSeverity(severity), categories, "reason", 10L, 0.001);
    }

    private static final List<ModerationResultItem> ITEMS = List.of(
            item(1, "ALLOW", Severity.NONE, List.of()),
            item(2, "BLOCK", Severity.HIGH, List.of("hate_speech", "violence")),
            item(3, "ALLOW", Severity.LOW, List.of()));

    /** An unclassified text: no verdict (belongs to the response error list). */
    private static final ModerationResultItem FAILED = item(9, null, Severity.UNKNOWN, List.of());

    @Test
    void groupsByVerdictPreservingOrder() {
        Map<String, List<ModerationResultItem>> grouped = ModerationMapping.groupByVerdict(ITEMS);
        assertEquals(2, grouped.size());
        assertEquals(List.of(1L, 3L), grouped.get("ALLOW").stream().map(ModerationResultItem::id).toList());
        assertEquals(List.of(2L), grouped.get("BLOCK").stream().map(ModerationResultItem::id).toList());
    }

    @Test
    void verdictCountsIncludeAllVerdicts() {
        Map<String, Integer> counts = ModerationMapping.verdictCounts(ITEMS);
        assertEquals(2, counts.get("ALLOW"));
        assertEquals(1, counts.get("BLOCK"));
    }

    @Test
    void itemsWithoutVerdictAreExcludedFromGroupingAndCounts() {
        Map<String, List<ModerationResultItem>> grouped = ModerationMapping.groupByVerdict(List.of(FAILED));
        assertEquals(2, grouped.size());
        assertTrue(grouped.get("ALLOW").isEmpty());
        assertTrue(grouped.get("BLOCK").isEmpty());
        assertEquals(0, ModerationMapping.verdictCounts(List.of(FAILED)).get("ALLOW"));
        assertEquals(0, ModerationMapping.verdictCounts(List.of(FAILED)).get("BLOCK"));
    }

    @Test
    void severityCountsAreOrderedAndComplete() {
        List<ModerationResultItem> all = new ArrayList<>(ITEMS);
        all.add(FAILED);
        Map<String, Integer> counts = ModerationMapping.severityCounts(all);
        assertEquals(List.of("NONE", "LOW", "MODERATE", "HIGH", "UNKNOWN"),
                List.copyOf(counts.keySet()));
        assertEquals(1, counts.get("NONE"));
        assertEquals(1, counts.get("LOW"));
        assertEquals(0, counts.get("MODERATE"));
        assertEquals(1, counts.get("HIGH"));
        assertEquals(1, counts.get("UNKNOWN"));
    }

    @Test
    void categoryCountsAggregateAlphabetically() {
        Map<String, Integer> counts = ModerationMapping.categoryCounts(ITEMS);
        assertEquals(1, counts.get("hate_speech"));
        assertEquals(1, counts.get("violence"));
        assertEquals(List.of("hate_speech", "violence"), List.copyOf(counts.keySet()));
    }

    @Test
    void riskFromSeverityIsMonotonic() {
        assertEquals(0.0, riskFromSeverity(Severity.NONE), EPS);
        assertEquals(1.0 / 3.0, riskFromSeverity(Severity.LOW), EPS);
        assertEquals(2.0 / 3.0, riskFromSeverity(Severity.MODERATE), EPS);
        assertEquals(1.0, riskFromSeverity(Severity.HIGH), EPS);
        assertEquals(0.0, riskFromSeverity(Severity.UNKNOWN), EPS);
    }
}
