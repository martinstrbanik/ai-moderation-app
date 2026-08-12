package sk.automoder.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import sk.automoder.model.BenchmarkLevel;

import java.util.List;

public record CreateBenchmarkRequest(
        @NotNull(message = "datasetId is required.") Long datasetId,
        Long policyId,
        @NotEmpty(message = "At least one model is required.") List<@NotNull Long> modelIds,
        @NotNull(message = "Benchmark level is required (EXTRA_LIGHT/LIGHT/FULL).") BenchmarkLevel level,
        Long apiKeyId,
        Integer batchSize
) {
}