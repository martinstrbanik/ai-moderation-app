package sk.automoder.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Computes benchmark metrics (macro-averaged precision/recall/F1 + accuracy).
 */
public final class Metrics {

    /** Aggregated metrics for a single model. */
    public record Summary(double precision, double recall, double f1, double accuracy) {
    }

    private Metrics() {
    }

    /**
     * @param expected  expected labels in sample order (never null entries)
     * @param predicted predicted labels in the same order (null = failed prediction)
     * @param labels    the full label set
     */
    public static Summary compute(List<String> expected, List<String> predicted, Set<String> labels) {
        Map<String, long[]> confusion = new TreeMap<>(); // label -> [tp, fp, fn]
        labels.forEach(l -> confusion.put(l, new long[3]));

        int correct = 0;
        for (int i = 0; i < expected.size(); i++) {
            String exp = expected.get(i);
            String pred = predicted.get(i);
            long[] expC = confusion.get(exp);
            if (pred == null) {
                expC[2]++; // false negative
                continue;
            }
            if (exp.equals(pred)) {
                correct++;
                expC[0]++;
            } else {
                expC[2]++; // false negative for expected
                confusion.get(pred)[1]++; // false positive for predicted
            }
        }

        double precisionSum = 0, recallSum = 0, f1Sum = 0;
        for (String label : labels) {
            long[] c = confusion.get(label);
            double precision = c[0] + c[1] == 0 ? 0.0 : (double) c[0] / (c[0] + c[1]);
            double recall = c[0] + c[2] == 0 ? 0.0 : (double) c[0] / (c[0] + c[2]);
            double f1 = precision + recall == 0 ? 0.0 : 2 * (precision * recall) / (precision + recall);
            precisionSum += precision;
            recallSum += recall;
            f1Sum += f1;
        }
        int n = Math.max(labels.size(), 1);
        double accuracy = expected.isEmpty() ? 0.0 : (double) correct / expected.size();
        return new Summary(precisionSum / n, recallSum / n, f1Sum / n, accuracy);
    }
}