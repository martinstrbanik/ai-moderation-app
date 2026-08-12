package sk.automoder.dto;

import sk.automoder.model.AiModel;
import sk.automoder.model.ModelType;

import java.time.Instant;

public record ModelResponse(
        Long id,
        String provider,
        String modelId,
        String name,
        ModelType type,
        boolean enabled,
        Instant createdAt
) {
    public static ModelResponse from(AiModel model) {
        return new ModelResponse(
                model.getId(),
                model.getProvider(),
                model.getModelId(),
                model.getName(),
                model.getType(),
                model.isEnabled(),
                model.getCreatedAt()
        );
    }
}