package sk.automoder.dto;

import jakarta.validation.constraints.NotBlank;

public record ApiKeyRequest(
        @NotBlank(message = "Key label is required.") String label,
        @NotBlank(message = "OpenRouter API key is required.") String key
) {
}