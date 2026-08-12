package sk.automoder.dto;

import sk.automoder.model.BenchmarkLevel;
import sk.automoder.model.BenchmarkResult;
import sk.automoder.model.BenchmarkRun;
import sk.automoder.model.RunStatus;

import java.time.Instant;
import java.util.List;

public record BenchmarkRunResponse(
        Long id,
        Long datasetId,
        String datasetName,
        Long policyId,
        String policyName,
        Long apiKeyId,
        BenchmarkLevel level,
        Integer batchSize,
        RunStatus status,
        boolean published,
        Instant startedAt,
        Instant finishedAt,
        List<BenchmarkResultResponse> results
) {
    public static BenchmarkRunResponse from(BenchmarkRun run, List<BenchmarkResult> results) {
        return new BenchmarkRunResponse(
                run.getId(),
                run.getDataset().getId(),
                run.getDataset().getName(),
                run.getPolicy() == null ? null : run.getPolicy().getId(),
                run.getPolicy() == null ? null : run.getPolicy().getName(),
                run.getApiKeyId(),
                run.getLevel(),
                run.getBatchSize(),
                run.getStatus(),
                run.isPublished(),
                run.getStartedAt(),
                run.getFinishedAt(),
                results.stream().map(BenchmarkResultResponse::from).toList()
        );
    }
}