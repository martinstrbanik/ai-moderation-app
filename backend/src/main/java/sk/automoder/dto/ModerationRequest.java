package sk.automoder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ModerationRequest(
        @NotNull(message = "policyId is required.") Long policyId,
        @NotBlank(message = "text is required.") String text
) {
}