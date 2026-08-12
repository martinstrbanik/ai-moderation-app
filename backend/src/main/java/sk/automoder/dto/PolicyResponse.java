package sk.automoder.dto;

import sk.automoder.model.Policy;
import sk.automoder.model.PolicyAction;

import java.time.Instant;

public record PolicyResponse(
        Long id,
        String tenantId,
        String name,
        String description,
        String categories,
        String rules,
        double threshold,
        PolicyAction action,
        Long modelId,
        Long fallbackModelId,
        boolean active,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
    public static PolicyResponse from(Policy policy) {
        return new PolicyResponse(
                policy.getId(),
                policy.getTenantId(),
                policy.getName(),
                policy.getDescription(),
                policy.getCategories(),
                policy.getRules(),
                policy.getThreshold(),
                policy.getAction(),
                policy.getModelId(),
                policy.getFallbackModelId(),
                policy.isActive(),
                policy.getVersion(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}