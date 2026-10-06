package sk.automoder.dto;

import sk.automoder.model.Category;
import sk.automoder.model.Policy;
import sk.automoder.model.Severity;

import java.time.Instant;
import java.util.List;

public record PolicyResponse(
        Long id,
        String tenantId,
        String name,
        String description,
        List<Category> categories,
        Severity thresholdSeverity,
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
                policy.getThresholdSeverity(),
                policy.getModelId(),
                policy.getFallbackModelId(),
                policy.isActive(),
                policy.getVersion(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}