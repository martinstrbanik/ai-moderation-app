package sk.automoder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import sk.automoder.model.Category;
import sk.automoder.model.Severity;

import java.util.List;

public record PolicyRequest(
        @NotBlank(message = "Policy name is required.") String name,
        String description,
        List<Category> categories,
        @NotNull(message = "Threshold severity is required (NONE/LOW/MODERATE/HIGH).")
        Severity thresholdSeverity,
        @NotNull(message = "Target model is required.") Long modelId,
        Long fallbackModelId,
        boolean active
) {
}