package sk.automoder.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import sk.automoder.model.BenchmarkLevel;
import sk.automoder.model.BenchmarkMode;

import java.util.List;

public record CreateBenchmarkRequest(
        @NotNull(message = "datasetId is required.") Long datasetId,
        Long policyId,
        @NotEmpty(message = "At least one model is required.") List<@NotNull Long> modelIds,
        @NotNull(message = "Benchmark level is required (DEBUG/EXTRA_LIGHT/LIGHT/FULL).") BenchmarkLevel level,
        Long apiKeyId,
        Integer batchSize,
        /**
         * CLASSIFICATION (default) or MODERATION. MODERATION requires a complete dataset
         * label -&gt; verdict mapping (via {@code PUT /api/datasets/{id}/label-verdicts});
         * {@code policyId} is not used for MODERATION - the categories and the expected
         * verdicts are both derived from the dataset.
         */
        BenchmarkMode mode
) {
}