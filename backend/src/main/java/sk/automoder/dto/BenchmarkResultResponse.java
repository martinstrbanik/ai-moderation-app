package sk.automoder.dto;

import sk.automoder.model.BenchmarkResult;
import sk.automoder.model.MetricScores;
import sk.automoder.model.Severity;

import java.util.Map;

public record BenchmarkResultResponse(
        Long id,
        Long runId,
        Long modelId,
        String modelName,
        Double precision,
        Double recall,
        Double f1,
        Double accuracy,
        Double avgLatency,
        Double cost,
        int errorCount,
        Integer processedSamples,
        /** Moderation mode only: metrics for every severity threshold (threshold sweep). */
        Map<Severity, MetricScores> thresholdMetrics
) {
    public static BenchmarkResultResponse from(BenchmarkResult result) {
        return new BenchmarkResultResponse(
                result.getId(),
                result.getRun().getId(),
                result.getModel().getId(),
                result.getModel().getName(),
                result.getPrecision(),
                result.getRecall(),
                result.getF1(),
                result.getAccuracy(),
                result.getAvgLatency(),
                result.getCost(),
                result.getErrorCount(),
                result.getProcessedSamples(),
                result.getThresholdMetrics()
        );
    }
}