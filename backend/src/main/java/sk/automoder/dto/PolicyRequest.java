package sk.automoder.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import sk.automoder.model.PolicyAction;

public record PolicyRequest(
        @NotBlank(message = "Policy name is required.") String name,
        String description,
        String categories,
        String rules,
        @Min(value = 0, message = "Threshold must be between 0 and 1.") double threshold,
        @NotNull(message = "Action is required (ALLOW/FLAG/BLOCK).") PolicyAction action,
        @NotNull(message = "Target model is required.") Long modelId,
        Long fallbackModelId,
        boolean active
) {
}