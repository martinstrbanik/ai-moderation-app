package sk.automoder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ModerationRequest(
        @NotNull(message = "policyId is required.") Long policyId,
        @NotEmpty(message = "items must contain at least one text.") @Valid List<ModerationItem> items,
        @Min(value = 1, message = "batchSize must be >= 1.")
        @Max(value = 100, message = "batchSize must be <= 100.")
        Integer batchSize
) {
    /**
     * A single text to moderate. {@code id} is an <b>external id</b> supplied by the
     * caller's system; the application additionally assigns its own internal id
     * (the 1-based position in the request) used for batching and output correlation.
     */
    public record ModerationItem(
            @NotNull(message = "id is required.") String id,
            @NotBlank(message = "text is required.") String text
    ) {
    }
}