package sk.automoder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import sk.automoder.model.ModelType;

public record ModelRequest(
        @NotBlank(message = "model_id is required.") String modelId,
        @NotBlank(message = "Model name is required.") String name,
        @NotNull(message = "Model type is required (TEXT/VISION).") ModelType type,
        boolean enabled
) {
}