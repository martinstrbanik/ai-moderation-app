package sk.automoder.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

/**
 * Moderation policy - defines categories and the target model for content review.
 */
@Entity
@Table(name = "policy")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /**
     * Categories the policy looks for, each a {@link Category} mini-prompt. Stored as
     * a JSON array in a {@code text} column via {@link CategoryListConverter}.
     */
    @Convert(converter = CategoryListConverter.class)
    @Column(columnDefinition = "text")
    private List<Category> categories;

    /**
     * Minimum severity that triggers a {@link PolicyAction#BLOCK}. Added nullable to
     * stay compatible with {@code ddl-auto=update} on a table that already has rows.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "threshold_severity", length = 16)
    private Severity thresholdSeverity;

    /** FK to the target model. */
    @Column(name = "model_id", nullable = false)
    private Long modelId;

    /** FK to the fallback model (optional). */
    @Column(name = "fallback_model_id")
    private Long fallbackModelId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    // ---------- getters & setters ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
    }

    public Severity getThresholdSeverity() {
        return thresholdSeverity;
    }

    public void setThresholdSeverity(Severity thresholdSeverity) {
        this.thresholdSeverity = thresholdSeverity;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public Long getFallbackModelId() {
        return fallbackModelId;
    }

    public void setFallbackModelId(Long fallbackModelId) {
        this.fallbackModelId = fallbackModelId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}