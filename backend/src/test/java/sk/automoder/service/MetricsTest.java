package sk.automoder.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsTest {

    private static final Set<String> BINARY = new LinkedHashSet<>(List.of("not_hate", "hate"));
    private static final double EPS = 1e-9;

    @Test
    void allCorrect() {
        Metrics.Summary s = Metrics.compute(
                List.of("hate", "not_hate", "hate"),
                List.of("hate", "not_hate", "hate"),
                BINARY);
        assertEquals(1.0, s.precision(), EPS);
        assertEquals(1.0, s.recall(), EPS);
        assertEquals(1.0, s.f1(), EPS);
        assertEquals(1.0, s.accuracy(), EPS);
    }

    @Test
    void balancedBinaryCross() {
        // per class TP=1 FP=1 FN=1 -> precision=recall=f1=0.5 (macro), accuracy=0.5
        Metrics.Summary s = Metrics.compute(
                List.of("hate", "hate", "not_hate", "not_hate"),
                List.of("hate", "not_hate", "not_hate", "hate"),
                BINARY);
        assertEquals(0.5, s.precision(), EPS);
        assertEquals(0.5, s.recall(), EPS);
        assertEquals(0.5, s.f1(), EPS);
        assertEquals(0.5, s.accuracy(), EPS);
    }

    @Test
    void failedPredictionIsIncorrect() {
        // null prediction = failed call -> treated as wrong prediction for the expected label
        Metrics.Summary s = Metrics.compute(
                List.of("not_hate", "hate"),
                Arrays.asList(null, null),
                BINARY);
        assertEquals(0.0, s.accuracy(), EPS);
        assertEquals(0.0, s.precision(), EPS);
        assertEquals(0.0, s.recall(), EPS);
        assertEquals(0.0, s.f1(), EPS);
    }
}