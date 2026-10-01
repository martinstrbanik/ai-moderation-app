package sk.automoder.service;

import org.junit.jupiter.api.Test;
import sk.automoder.dto.ModerationResponse.ModerationResultItem;
import sk.automoder.model.PolicyAction;
import sk.automoder.model.Severity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static sk.automoder.dto.ModerationResponse.riskFromSeverity;

class ModerationMappingTest {

    private static final double EPS = 1e-9;

    // ---------- verdict mapping ----------

    @Test
    void moderateThresholdTriggersOnModerateAndHigh() {
        assertEquals(PolicyAction.ALLOW,
                ModerationMapping.mapVerdict(Severity.MODERATE, PolicyAction.BLOCK, Severity.NONE));
        assertEquals(PolicyAction.ALLOW,
                ModerationMapping.mapVerdict(Severity.MODERATE, PolicyAction.BLOCK, Severity.LOW));
        assertEquals(PolicyAction.BLOCK,
                ModerationMapping.mapVerdict(Severity.MODERATE, PolicyAction.BLOCK, Severity.MODERATE));
        assertEquals(PolicyAction.BLOCK,
                ModerationMapping.mapVerdict(Severity.MODERATE, PolicyAction.BLOCK, Severity.HIGH));
    }

    @Test
    void highThresholdOnlyTriggersOnHigh() {
        assertEquals(PolicyAction.ALLOW,
                ModerationMapping.mapVerdict(Severity.HIGH, PolicyAction.FLAG, Severity.MODERATE));
        assertEquals(PolicyAction.FLAG,
                ModerationMapping.mapVerdict(Severity.HIGH, PolicyAction.FLAG, Severity.HIGH));
    }

    @Test
    void noneThresholdAlwaysAppliesAction() {
        assertEquals(PolicyAction.BLOCK,
                ModerationMapping.mapVerdict(Severity.NONE, PolicyAction.BLOCK, Severity.NONE));
    }

    @Test
    void unknownSeverityFallsBackToAllow() {
        // mapVerdict alone treats unknown severity like NONE; the service overrides
        // unclassified items to FLAG explicitly.
        assertEquals(PolicyAction.ALLOW,
                ModerationMapping.mapVerdict(Severity.MODERATE, PolicyAction.BLOCK, Severity.UNKNOWN));
    }

    // ---------- grouping & counts ----------

    private static ModerationResultItem item(long id, String verdict, Severity severity, List<String> categories) {
        return new ModerationResultItem(id, "ext-" + id, "text " + id, verdict, severity,
                riskFromSeverity(severity), categories, "reason", 10L, 0.001);
    }

    private static final List<ModerationResultItem> ITEMS = List.of(
            item(1, "ALLOW", Severity.NONE, List.of()),
            item(2, "BLOCK", Severity.HIGH, List.of("hate_speech", "violence")),
            item(3, "ALLOW", Severity.LOW, List.of()),
            item(4, "FLAG", Severity.UNKNOWN, List.of()));

    @Test
    void groupsByVerdictPreservingOrder() {
        Map<String, List<ModerationResultItem>> grouped = ModerationMapping.groupByVerdict(ITEMS);
        assertEquals(3, grouped.size());
        assertEquals(List.of(1L, 3L), grouped.get("ALLOW").stream().map(ModerationResultItem::id).toList());
        assertEquals(List.of(4L), grouped.get("FLAG").stream().map(ModerationResultItem::id).toList());
        assertEquals(List.of(2L), grouped.get("BLOCK").stream().map(ModerationResultItem::id).toList());
    }

    @Test
    void verdictCountsIncludeAllVerdicts() {
        Map<String, Integer> counts = ModerationMapping.verdictCounts(ITEMS);
        assertEquals(2, counts.get("ALLOW"));
        assertEquals(1, counts.get("FLAG"));
        assertEquals(1, counts.get("BLOCK"));
    }

    @Test
    void severityCountsAreOrderedAndComplete() {
        Map<String, Integer> counts = ModerationMapping.severityCounts(ITEMS);
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
