package sk.automoder.dto;

import sk.automoder.model.BenchmarkResult;

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
        Integer processedSamples
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
                result.getProcessedSamples()
        );
    }
}